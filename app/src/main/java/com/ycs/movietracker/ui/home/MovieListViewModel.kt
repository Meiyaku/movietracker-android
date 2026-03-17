package com.ycs.movietracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.repository.FirebaseMovieListRepository
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MovieListViewModel(
    private val listRepository: MovieListRepository = FirebaseMovieListRepository(),
    private val movieRepository: MovieRepository = FirebaseMovieRepository()
) : ViewModel() {

    private val _lists = MutableStateFlow<List<MovieList>>(emptyList())
    val lists: StateFlow<List<MovieList>> = _lists.asStateFlow()

    private val _activeList = MutableStateFlow<MovieList?>(null)
    val activeList: StateFlow<MovieList?> = _activeList.asStateFlow()

    private val _createListError = MutableStateFlow<String?>(null)
    val createListError: StateFlow<String?> = _createListError.asStateFlow()

    private val _isCreatingList = MutableStateFlow(false)
    val isCreatingList: StateFlow<Boolean> = _isCreatingList.asStateFlow()

    private val _createListSuccess = MutableStateFlow(false)
    val createListSuccess: StateFlow<Boolean> = _createListSuccess.asStateFlow()

    fun loadLists(uid: String) {
        if (uid.isBlank()) return
        viewModelScope.launch {
            listRepository.getLists(uid).collect { rawLists ->
                val sorted = sort(rawLists)
                _lists.value = sorted

                // Keep the current active selection if it still exists in the new list;
                // otherwise fall back to "My Movies" (or the first list if absent).
                val currentActive = _activeList.value
                val stillExists = currentActive != null && sorted.any { it.id == currentActive.id }
                if (!stillExists) {
                    _activeList.value = sorted.firstOrNull { it.name == "My Movies" }
                        ?: sorted.firstOrNull()
                }
            }
        }
    }

    fun selectList(list: MovieList) {
        _activeList.value = list
    }

    fun createList(name: String, uid: String) {
        val trimmedName = name.trim()
        val isDuplicate = _lists.value.any { it.name.trim().equals(trimmedName, ignoreCase = true) }
        if (isDuplicate) {
            _createListError.value = "A list with this name already exists"
            return
        }
        viewModelScope.launch {
            _isCreatingList.value = true
            _createListError.value = null
            val result = listRepository.createList(uid, MovieList(name = trimmedName))
            _isCreatingList.value = false
            result.onSuccess { id ->
                selectList(MovieList(id = id, name = trimmedName))
                _createListSuccess.value = true
            }
            result.onFailure {
                _createListError.value = "Failed to create list. Please try again."
            }
        }
    }

    fun clearCreateListError() {
        _createListError.value = null
    }

    fun clearCreateListSuccess() {
        _createListSuccess.value = false
    }

    private val _renameListError = MutableStateFlow<String?>(null)
    val renameListError: StateFlow<String?> = _renameListError.asStateFlow()

    private val _isRenamingList = MutableStateFlow(false)
    val isRenamingList: StateFlow<Boolean> = _isRenamingList.asStateFlow()

    private val _renameListSuccess = MutableStateFlow(false)
    val renameListSuccess: StateFlow<Boolean> = _renameListSuccess.asStateFlow()

    fun renameList(list: MovieList, newName: String, uid: String) {
        val trimmedName = newName.trim()
        val isDuplicate = _lists.value.any {
            it.id != list.id && it.name.trim().equals(trimmedName, ignoreCase = true)
        }
        if (isDuplicate) {
            _renameListError.value = "A list with this name already exists"
            return
        }
        viewModelScope.launch {
            _isRenamingList.value = true
            _renameListError.value = null
            val result = listRepository.updateList(uid, list.copy(name = trimmedName))
            _isRenamingList.value = false
            result.onSuccess {
                if (_activeList.value?.id == list.id) {
                    _activeList.value = _activeList.value?.copy(name = trimmedName)
                }
                _renameListSuccess.value = true
            }
            result.onFailure {
                _renameListError.value = "Failed to rename list. Please try again."
            }
        }
    }

    fun clearRenameListError() {
        _renameListError.value = null
    }

    fun clearRenameListSuccess() {
        _renameListSuccess.value = false
    }

    private val _deleteListError = MutableStateFlow<String?>(null)
    val deleteListError: StateFlow<String?> = _deleteListError.asStateFlow()

    private val _isDeletingList = MutableStateFlow(false)
    val isDeletingList: StateFlow<Boolean> = _isDeletingList.asStateFlow()

    private val _deleteListSuccess = MutableStateFlow(false)
    val deleteListSuccess: StateFlow<Boolean> = _deleteListSuccess.asStateFlow()

    fun deleteList(list: MovieList, uid: String) {
        viewModelScope.launch {
            _isDeletingList.value = true
            _deleteListError.value = null

            val removeResult = movieRepository.removeListFromMovies(uid, list.id)
            if (removeResult.isFailure) {
                _isDeletingList.value = false
                _deleteListError.value = "Failed to delete list. Please try again."
                return@launch
            }

            val deleteResult = listRepository.deleteList(uid, list.id)
            _isDeletingList.value = false
            deleteResult.onSuccess {
                if (_activeList.value?.id == list.id) {
                    val updatedLists = _lists.value.filter { it.id != list.id }
                    _activeList.value = updatedLists.firstOrNull { it.name == "My Movies" }
                        ?: updatedLists.firstOrNull()
                }
                _deleteListSuccess.value = true
            }
            deleteResult.onFailure {
                _deleteListError.value = "Failed to delete list. Please try again."
            }
        }
    }

    fun clearDeleteListSuccess() {
        _deleteListSuccess.value = false
    }

    companion object {
        /** "My Movies" always first; remaining lists sorted A–Z (case-insensitive). */
        internal fun sort(lists: List<MovieList>): List<MovieList> {
            val myMovies = lists.filter { it.name == "My Movies" }
            val others = lists.filter { it.name != "My Movies" }
                .sortedBy { it.name.lowercase() }
            return myMovies + others
        }
    }
}
