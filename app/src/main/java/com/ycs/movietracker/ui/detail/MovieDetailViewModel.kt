package com.ycs.movietracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.data.repository.TmdbRepository
import com.ycs.movietracker.util.AppConfig
import com.ycs.movietracker.util.ConnectivityMonitor
import com.ycs.movietracker.util.StringProvider
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class MovieLoadState {
    object Idle : MovieLoadState()
    object Loading : MovieLoadState()
    data class Error(val message: String) : MovieLoadState()
}

sealed class DetailOperationState {
    object Idle : DetailOperationState()
    object Saving : DetailOperationState()
    object Deleting : DetailOperationState()
    data class SaveSuccess(val movieId: String) : DetailOperationState()
    object DeleteSuccess : DetailOperationState()
    data class Error(val message: String) : DetailOperationState()
}

data class DraftState(
    val title: String = "",
    val year: String = "",
    val genre: String = "",
    val description: String = "",
    val notes: String = "",
    val trailerUrl: String = "",
    val posterUrl: String = "",
    val isWatched: Boolean = false,
    val rating: Double = 0.0,
    val selectedListIds: Set<String> = emptySet(),
    val tmdbId: Int? = null,
    val tmdbMediaType: String? = null
)

data class DraftErrors(
    val title: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trailerUrl: String? = null,
    val posterUrl: String? = null
) {
    val hasErrors: Boolean
        get() = title != null || year != null || genre != null || trailerUrl != null || posterUrl != null
}

/**
 * ViewModel for the Movie Detail screen.
 *
 * [existingMovie] is null when adding a new movie; non-null when editing an existing one.
 * [isEditMode] starts true for new movies (edit immediately) and false for existing ones
 * (view mode first).
 */
class MovieDetailViewModel(
    private val movieRepository: MovieRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    val tmdbRepository: TmdbRepository,
    private val strings: StringProvider,
    private val connectivityMonitor: ConnectivityMonitor,
    private val uid: String,
    private val movieId: String,
    existingMovie: Movie?
) : ViewModel() {

    var existingMovie: Movie? = existingMovie
        private set

    val isTmdbSearchEnabled = remoteConfigRepository.isTmdbSearchEnabled

    private val _loadState = MutableStateFlow<MovieLoadState>(MovieLoadState.Idle)
    val loadState: StateFlow<MovieLoadState> = _loadState.asStateFlow()

    init {
        if (movieId != "new" && existingMovie == null) {
            fetchMovie()
        }
    }

    private fun fetchMovie() {
        _loadState.value = MovieLoadState.Loading
        viewModelScope.launch {
            movieRepository.getMovieById(uid, movieId)
                .onSuccess { movie ->
                    existingMovie = movie
                    _draft.value = DraftState(
                        title = movie.title,
                        year = movie.year?.toString() ?: "",
                        genre = movie.genre ?: "",
                        description = movie.description ?: "",
                        notes = movie.notes ?: "",
                        trailerUrl = movie.trailerUrl ?: "",
                        posterUrl = movie.posterUrl ?: "",
                        isWatched = movie.status == WatchStatus.WATCHED,
                        rating = movie.rating ?: 0.0,
                        selectedListIds = movie.listIds.toSet(),
                        tmdbId = movie.tmdbId,
                        tmdbMediaType = movie.tmdbMediaType
                    )
                    _isEditMode.value = false
                    _loadState.value = MovieLoadState.Idle
                }
                .onFailure { e ->
                    Timber.e(e, "fetchMovie failed [uid=$uid, movieId=$movieId]")
                    _loadState.value = MovieLoadState.Error(
                        e.message ?: strings.get(R.string.error_generic)
                    )
                }
        }
    }

    // ── Touched field tracking ────────────────────────────────────────────────
    // Tracks which fields the user has interacted with. Errors are only shown for
    // touched fields, keeping pristine fields clean until Save is pressed.

    private val touchedFields = mutableSetOf<String>()

    // ── Mode ─────────────────────────────────────────────────────────────────

    private val _isEditMode = MutableStateFlow(movieId == "new")
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // ── Draft state ───────────────────────────────────────────────────────────

    private val _draft = MutableStateFlow(
        DraftState(
            title = existingMovie?.title ?: "",
            year = existingMovie?.year?.toString() ?: "",
            genre = existingMovie?.genre ?: "",
            description = existingMovie?.description ?: "",
            notes = existingMovie?.notes ?: "",
            trailerUrl = existingMovie?.trailerUrl ?: "",
            posterUrl = existingMovie?.posterUrl ?: "",
            isWatched = existingMovie?.status == WatchStatus.WATCHED,
            rating = existingMovie?.rating ?: 0.0,
            selectedListIds = existingMovie?.listIds?.toSet() ?: emptySet()
        )
    )
    val draft: StateFlow<DraftState> = _draft.asStateFlow()

    /**
     * Updates the draft and re-validates any field the user has touched.
     * Errors for untouched fields are not shown until [save] is called, following
     * the standard "validate on interaction" pattern.
     */
    fun updateDraft(newDraft: DraftState) {
        val old = _draft.value
        _draft.value = newDraft

        if (newDraft.title      != old.title)      touchedFields += "title"
        if (newDraft.year       != old.year)       touchedFields += "year"
        if (newDraft.genre      != old.genre)      touchedFields += "genre"
        if (newDraft.trailerUrl != old.trailerUrl) touchedFields += "trailerUrl"
        if (newDraft.posterUrl  != old.posterUrl)  touchedFields += "posterUrl"

        if (touchedFields.isEmpty()) return

        val fresh = validate(newDraft)
        val current = _draftErrors.value
        _draftErrors.value = DraftErrors(
            title      = if ("title"      in touchedFields) fresh.title      else current.title,
            year       = if ("year"       in touchedFields) fresh.year       else current.year,
            genre      = if ("genre"      in touchedFields) fresh.genre      else current.genre,
            trailerUrl = if ("trailerUrl" in touchedFields) fresh.trailerUrl else current.trailerUrl,
            posterUrl  = if ("posterUrl"  in touchedFields) fresh.posterUrl  else current.posterUrl,
        )
    }

    // ── Operation state ───────────────────────────────────────────────────────

    private val _operationState = MutableStateFlow<DetailOperationState>(DetailOperationState.Idle)
    val operationState: StateFlow<DetailOperationState> = _operationState.asStateFlow()

    private val _showDeleteConfirm = MutableStateFlow(false)
    val showDeleteConfirm: StateFlow<Boolean> = _showDeleteConfirm.asStateFlow()

    private val _showDuplicateWarning = MutableStateFlow(false)
    val showDuplicateWarning: StateFlow<Boolean> = _showDuplicateWarning.asStateFlow()

    private val _draftErrors = MutableStateFlow(DraftErrors())
    val draftErrors: StateFlow<DraftErrors> = _draftErrors.asStateFlow()

    fun requestDeleteConfirm() { _showDeleteConfirm.value = true }
    fun dismissDeleteConfirm() { _showDeleteConfirm.value = false }
    fun dismissDuplicateWarning() { _showDuplicateWarning.value = false }

    /** The full Movie object from the most recent successful save, including the Firestore-assigned ID. */
    private val _lastSavedMovie = MutableStateFlow<Movie?>(null)
    val lastSavedMovie: StateFlow<Movie?> = _lastSavedMovie.asStateFlow()

    // ── Actions ───────────────────────────────────────────────────────────────

    fun enterEditMode() {
        _isEditMode.value = true
    }

    /** Discard unsaved changes and return to view mode. Only valid for existing movies;
     *  for new movies the caller should navigate back instead. */
    fun cancelEdit() {
        val movie = existingMovie ?: return
        _draft.value = DraftState(
            title = movie.title,
            year = movie.year?.toString() ?: "",
            genre = movie.genre ?: "",
            description = movie.description ?: "",
            notes = movie.notes ?: "",
            trailerUrl = movie.trailerUrl ?: "",
            posterUrl = movie.posterUrl ?: "",
            isWatched = movie.status == WatchStatus.WATCHED,
            rating = movie.rating ?: 0.0,
            selectedListIds = movie.listIds.toSet()
        )
        _draftErrors.value = DraftErrors()
        touchedFields.clear()
        _isEditMode.value = false
    }

    /**
     * Validates all editable fields and returns a [DraftErrors] describing any failures.
     *
     * Year bounds: [AppConfig.MIN_MOVIE_YEAR] (first known motion picture) to the current year
     * plus [AppConfig.MAX_FUTURE_YEAR_OFFSET] (announced/upcoming releases).
     * Trailer and poster URLs must begin with `http://` or `https://`; empty values are accepted.
     */
    private fun validate(d: DraftState): DraftErrors {
        val maxYear = Calendar.getInstance().get(Calendar.YEAR) + AppConfig.MAX_FUTURE_YEAR_OFFSET
        return DraftErrors(
            title = when {
                d.title.trim().isEmpty()              -> strings.get(R.string.error_title_required)
                d.title.length > AppConfig.MAX_TITLE_LENGTH -> strings.get(R.string.error_title_too_long)
                else -> null
            },
            year = d.year.takeIf { it.isNotEmpty() }?.let {
                val y = it.toIntOrNull()
                when {
                    y == null               -> strings.get(R.string.error_year_invalid, maxYear)
                    y < AppConfig.MIN_MOVIE_YEAR -> strings.get(R.string.error_year_invalid, maxYear)
                    y > maxYear             -> strings.get(R.string.error_year_invalid, maxYear)
                    else -> null
                }
            },
            genre = if (d.genre.length > AppConfig.MAX_GENRE_LENGTH) strings.get(R.string.error_genre_too_long) else null,
            trailerUrl = d.trailerUrl.trim().takeIf { it.isNotEmpty() }?.let {
                if (!it.startsWith("http://") && !it.startsWith("https://"))
                    strings.get(R.string.error_url_invalid) else null
            },
            posterUrl = d.posterUrl.trim().takeIf { it.isNotEmpty() }?.let {
                if (!it.startsWith("http://") && !it.startsWith("https://"))
                    strings.get(R.string.error_url_invalid) else null
            },
        )
    }

    fun save() {
        if (!connectivityMonitor.isOnline) {
            _operationState.value = DetailOperationState.Error(strings.get(R.string.error_offline))
            return
        }
        val d = _draft.value
        val errors = validate(d)
        if (errors.hasErrors) {
            _draftErrors.value = errors
            // Mark all errored fields as touched so typing immediately re-validates them
            if (errors.title      != null) touchedFields += "title"
            if (errors.year       != null) touchedFields += "year"
            if (errors.genre      != null) touchedFields += "genre"
            if (errors.trailerUrl != null) touchedFields += "trailerUrl"
            if (errors.posterUrl  != null) touchedFields += "posterUrl"
            return
        }
        _operationState.value = DetailOperationState.Saving
        val title       = d.title.trim()
        val year        = d.year.toIntOrNull()
        val genre       = d.genre.trim().ifEmpty { null }
        val status      = if (d.isWatched) WatchStatus.WATCHED else WatchStatus.WANT_TO_WATCH
        val rating      = if (d.isWatched && d.rating > 0) d.rating else null
        val description = d.description.trim().ifEmpty { null }
        val notes       = d.notes.trim().ifEmpty { null }
        val trailerUrl  = d.trailerUrl.trim().ifEmpty { null }
        val posterUrl   = d.posterUrl.trim().ifEmpty { null }
        val listIds     = d.selectedListIds.toList()
        viewModelScope.launch {
            val snapshot = existingMovie
            val excludeId = snapshot?.id
            // Non-atomic check-then-act: a concurrent save from another device with the same
            // title/year/genre can slip through this guard. See MovieRepository.checkDuplicate.
            val duplicateCheck = movieRepository.checkDuplicate(uid, title, year, genre, excludeId)
            if (duplicateCheck.isFailure) {
                _operationState.value = DetailOperationState.Error(strings.get(R.string.error_generic))
                return@launch
            }
            if (duplicateCheck.getOrDefault(false)) {
                _operationState.value = DetailOperationState.Idle
                _showDuplicateWarning.value = true
                return@launch
            }
            performSave(title, year, genre, status, rating, description, notes, trailerUrl, posterUrl, listIds, d.tmdbId, d.tmdbMediaType)
        }
    }

    fun saveIgnoringDuplicate() {
        if (!connectivityMonitor.isOnline) {
            _operationState.value = DetailOperationState.Error(strings.get(R.string.error_offline))
            return
        }
        _showDuplicateWarning.value = false
        val d = _draft.value
        val title       = d.title.trim()
        val year        = d.year.toIntOrNull()
        val genre       = d.genre.trim().ifEmpty { null }
        val status      = if (d.isWatched) WatchStatus.WATCHED else WatchStatus.WANT_TO_WATCH
        val rating      = if (d.isWatched && d.rating > 0) d.rating else null
        val description = d.description.trim().ifEmpty { null }
        val notes       = d.notes.trim().ifEmpty { null }
        val trailerUrl  = d.trailerUrl.trim().ifEmpty { null }
        val posterUrl   = d.posterUrl.trim().ifEmpty { null }
        val listIds     = d.selectedListIds.toList()
        _operationState.value = DetailOperationState.Saving
        viewModelScope.launch {
            performSave(title, year, genre, status, rating, description, notes, trailerUrl, posterUrl, listIds, d.tmdbId, d.tmdbMediaType)
        }
    }

    private suspend fun performSave(
        title: String, year: Int?, genre: String?, status: WatchStatus, rating: Double?,
        description: String?, notes: String?, trailerUrl: String?, posterUrl: String?,
        listIds: List<String>, tmdbId: Int?, tmdbMediaType: String?
    ) {
        val snapshot = existingMovie
        if (snapshot == null) {
            val newMovie = NewMovie(
                title = title, year = year, genre = genre, status = status, rating = rating,
                description = description, notes = notes, trailerUrl = trailerUrl,
                posterUrl = posterUrl, listIds = listIds, createdAt = Timestamp.now(),
                tmdbId = tmdbId, tmdbMediaType = tmdbMediaType
            )
            movieRepository.addMovie(uid, newMovie)
                .onSuccess { savedMovie ->
                    _lastSavedMovie.value = savedMovie
                    _isEditMode.value = false
                    _operationState.value = DetailOperationState.SaveSuccess(savedMovie.id)
                }
                .onFailure {
                    Timber.e(it, "save failed [uid=$uid]")
                    _operationState.value = DetailOperationState.Error(it.message ?: strings.get(R.string.error_generic))
                }
        } else {
            val updatedMovie = Movie(
                id = snapshot.id, title = title, year = year, genre = genre,
                status = status, rating = rating, description = description, notes = notes,
                trailerUrl = trailerUrl, posterUrl = posterUrl, listIds = listIds,
                createdAt = snapshot.createdAt,
                tmdbId = tmdbId, tmdbMediaType = tmdbMediaType,
                tmdbLookupAttempted = tmdbId != null || snapshot.tmdbLookupAttempted
            )
            movieRepository.updateMovie(uid, updatedMovie)
                .onSuccess {
                    _lastSavedMovie.value = updatedMovie
                    _isEditMode.value = false
                    _operationState.value = DetailOperationState.SaveSuccess(updatedMovie.id)
                }
                .onFailure {
                    Timber.e(it, "save failed [uid=$uid, movieId=${snapshot.id}]")
                    _operationState.value = DetailOperationState.Error(it.message ?: strings.get(R.string.error_generic))
                }
        }
    }

    fun delete() {
        if (!connectivityMonitor.isOnline) {
            _operationState.value = DetailOperationState.Error(strings.get(R.string.error_offline))
            return
        }
        val id = existingMovie?.id ?: return
        _operationState.value = DetailOperationState.Deleting
        viewModelScope.launch {
            movieRepository.deleteMovie(uid, id)
                .onSuccess { _operationState.value = DetailOperationState.DeleteSuccess }
                .onFailure {
                    Timber.e(it, "delete failed [uid=$uid, movieId=$id]")
                    _operationState.value = DetailOperationState.Error(it.message ?: strings.get(R.string.error_generic))
                }
        }
    }

    fun resetOperationState() { _operationState.value = DetailOperationState.Idle }

    private val _isRedetectingMediaType = MutableStateFlow(false)
    val isRedetectingMediaType: StateFlow<Boolean> = _isRedetectingMediaType.asStateFlow()

    private val _redetectMediaTypeError = MutableStateFlow<String?>(null)
    val redetectMediaTypeError: StateFlow<String?> = _redetectMediaTypeError.asStateFlow()

    /** Re-probes TMDB to correct a wrong media type on the current movie's tmdbId. */
    fun redetectMediaType() {
        val movie = existingMovie ?: return
        val tmdbId = movie.tmdbId ?: return
        if (_isRedetectingMediaType.value) return
        _isRedetectingMediaType.value = true
        _redetectMediaTypeError.value = null
        viewModelScope.launch {
            try {
                tmdbRepository.lookupMediaType(tmdbId, movie.title).fold(
                    onSuccess = { resolved ->
                        if (resolved == null) {
                            _redetectMediaTypeError.value =
                                "TMDB couldn't find this id as a movie or TV show."
                            return@fold
                        }
                        movieRepository.setTmdbLookupResult(uid, movie.id, tmdbId, resolved)
                            .onSuccess {
                                val updated = movie.copy(tmdbMediaType = resolved)
                                existingMovie = updated
                                _lastSavedMovie.value = updated
                                _draft.value = _draft.value.copy(tmdbMediaType = resolved)
                            }
                            .onFailure {
                                _redetectMediaTypeError.value =
                                    it.message ?: strings.get(R.string.error_generic)
                            }
                    },
                    onFailure = {
                        _redetectMediaTypeError.value =
                            it.message ?: strings.get(R.string.error_generic)
                    }
                )
            } finally {
                _isRedetectingMediaType.value = false
            }
        }
    }
}
