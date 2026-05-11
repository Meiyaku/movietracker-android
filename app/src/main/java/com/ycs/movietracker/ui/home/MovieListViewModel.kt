package com.ycs.movietracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.MovieListRepository
import android.content.Context
import com.ycs.movietracker.R
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.util.ConnectivityMonitor
import com.ycs.movietracker.util.toUserMessage
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MovieListViewModel(
    private val listRepository: MovieListRepository,
    private val movieRepository: MovieRepository,
    private val context: Context,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private val _uid = MutableStateFlow<String?>(null)

    private val _lists = MutableStateFlow<List<MovieList>>(emptyList())
    val lists: StateFlow<List<MovieList>> = _lists.asStateFlow()

    private val _activeList = MutableStateFlow<MovieList?>(null)
    val activeList: StateFlow<MovieList?> = _activeList.asStateFlow()

    private val _isLoadingLists = MutableStateFlow(false)
    val isLoadingLists: StateFlow<Boolean> = _isLoadingLists.asStateFlow()

    private val _listLoadError = MutableStateFlow<String?>(null)
    val listLoadError: StateFlow<String?> = _listLoadError.asStateFlow()

    fun clearListLoadError() { _listLoadError.value = null }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLists() = _uid.filterNotNull().flatMapLatest { uid ->
        listRepository.getLists(uid)
            .onStart { _isLoadingLists.value = true }
            .onEach { result ->
                _isLoadingLists.value = false
                result.onSuccess { rawLists ->
                    _listLoadError.value = null
                    val sorted = sort(rawLists)
                    _lists.value = sorted
                    val currentActive = _activeList.value
                    val stillExists = currentActive != null && sorted.any { it.id == currentActive.id }
                    if (!stillExists) {
                        val newActive = sorted.firstOrNull { it.isDefault }
                            ?: sorted.firstOrNull()
                        _activeList.value = newActive
                    }
                }
                result.onFailure { error ->
                    Timber.e(error, "loadLists failed [uid=$uid]")
                    _listLoadError.value = error.toUserMessage(context)
                }
            }
    }

    init {
        // Collect observeLists() in init. When uid changes, flatMapLatest cancels the previous
        // inner flow, triggering awaitClose { listener.remove() } in callbackFlow.
        viewModelScope.launch {
            observeLists().collect {}
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

    private val _createState = MutableStateFlow<ListMutationState>(ListMutationState.Idle)
    val createState: StateFlow<ListMutationState> = _createState.asStateFlow()

    // ── Edit ─────────────────────────────────────────────────────────────────

    private val _editState = MutableStateFlow<ListMutationState>(ListMutationState.Idle)
    val editState: StateFlow<ListMutationState> = _editState.asStateFlow()

    // ── Delete ────────────────────────────────────────────────────────────────

    private val _deleteState = MutableStateFlow<ListMutationState>(ListMutationState.Idle)
    val deleteState: StateFlow<ListMutationState> = _deleteState.asStateFlow()

    // ── Actions ───────────────────────────────────────────────────────────────

    fun loadLists(uid: String) {
        if (uid.isBlank()) return
        _uid.value = uid
    }

    fun selectList(list: MovieList) {
        _activeList.value = list
    }

    fun createList(name: String, subtitle: String?, description: String?, uid: String) {
        val trimmedName = name.trim()
        if (!connectivityMonitor.isOnline) {
            _createState.value = ListMutationState.Error(context.getString(R.string.error_offline))
            return
        }
        if (trimmedName.length > AppConfig.MAX_LIST_NAME_LENGTH) {
            _createState.value = ListMutationState.Error(context.getString(R.string.error_list_name_too_long))
            return
        }
        if (_lists.value.any { it.name.trim().equals(trimmedName, ignoreCase = true) }) {
            _createState.value = ListMutationState.Error(context.getString(R.string.error_duplicate_list_name))
            return
        }
        viewModelScope.launch {
            _createState.value = ListMutationState.Loading
            val list = MovieList(name = trimmedName, subtitle = subtitle, description = description)
            val result = listRepository.createList(uid, list)
            result.onSuccess { id ->
                selectList(MovieList(id = id, name = trimmedName, subtitle = subtitle, description = description))
                _createState.value = ListMutationState.Success
            }
            result.onFailure {
                Timber.e(it, "createList failed [uid=$uid, name=$trimmedName]")
                _createState.value = ListMutationState.Error(context.getString(R.string.error_create_list_failed))
            }
        }
    }

    fun resetCreateState() { _createState.value = ListMutationState.Idle }

    fun editList(list: MovieList, name: String, subtitle: String?, description: String?, uid: String) {
        val trimmedName = name.trim()
        if (!connectivityMonitor.isOnline) {
            _editState.value = ListMutationState.Error(context.getString(R.string.error_offline))
            return
        }
        if (trimmedName.length > AppConfig.MAX_LIST_NAME_LENGTH) {
            _editState.value = ListMutationState.Error(context.getString(R.string.error_list_name_too_long))
            return
        }
        val isDuplicate = _lists.value.any {
            it.id != list.id && it.name.trim().equals(trimmedName, ignoreCase = true)
        }
        if (isDuplicate) {
            _editState.value = ListMutationState.Error(context.getString(R.string.error_duplicate_list_name))
            return
        }
        viewModelScope.launch {
            _editState.value = ListMutationState.Loading
            val updated = list.copy(name = trimmedName, subtitle = subtitle, description = description)
            val result = listRepository.updateList(uid, updated)
            result.onSuccess {
                if (_activeList.value?.id == list.id) {
                    _activeList.value = updated
                }
                _editState.value = ListMutationState.Success
            }
            result.onFailure {
                Timber.e(it, "editList failed [uid=$uid, listId=${list.id}]")
                _editState.value = ListMutationState.Error(context.getString(R.string.error_edit_list_failed))
            }
        }
    }

    fun resetEditState() { _editState.value = ListMutationState.Idle }

    fun deleteList(list: MovieList, uid: String) {
        if (!connectivityMonitor.isOnline) {
            _deleteState.value = ListMutationState.Error(context.getString(R.string.error_offline))
            return
        }
        viewModelScope.launch {
            _deleteState.value = ListMutationState.Loading

            val removeResult = movieRepository.removeListFromMovies(uid, list.id)
            if (removeResult.isFailure) {
                Timber.e(removeResult.exceptionOrNull(), "removeListFromMovies failed [uid=$uid, listId=${list.id}]")
                _deleteState.value = ListMutationState.Error(context.getString(R.string.error_delete_list_failed))
                return@launch
            }

            val deleteResult = listRepository.deleteList(uid, list.id)
            deleteResult.onSuccess {
                if (_activeList.value?.id == list.id) {
                    val updatedLists = _lists.value.filter { it.id != list.id }
                    val newActive = updatedLists.firstOrNull { it.isDefault }
                        ?: updatedLists.firstOrNull()
                    _activeList.value = newActive
                }
                _deleteState.value = ListMutationState.Success
            }
            deleteResult.onFailure {
                Timber.e(it, "deleteList failed [uid=$uid, listId=${list.id}]")
                _deleteState.value = ListMutationState.Error(context.getString(R.string.error_delete_list_failed))
            }
        }
    }

    fun resetDeleteState() { _deleteState.value = ListMutationState.Idle }

    companion object {
        /**
         * Returns [lists] with MovieList.DEFAULT_LIST_NAME pinned at position 0 and all other lists
         * sorted A–Z case-insensitively. MovieList.DEFAULT_LIST_NAME is the default list created for every
         * new user; pinning it avoids it jumping around as the user renames other lists.
         */
        internal fun sort(lists: List<MovieList>): List<MovieList> {
            val myMovies = lists.filter { it.isDefault }
            val others = lists.filter { !it.isDefault }
                .sortedBy { it.name.lowercase() }
            return myMovies + others
        }
    }
}
