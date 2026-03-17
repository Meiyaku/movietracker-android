package com.ycs.movietracker.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the Movie Detail screen.
 *
 * [existingMovie] is null when adding a new movie; non-null when editing an existing one.
 * [isEditMode] starts true for new movies (edit immediately) and false for existing ones
 * (view mode first).
 */
class MovieDetailViewModel(
    private val movieRepository: MovieRepository,
    private val uid: String,
    val existingMovie: Movie? = null
) : ViewModel() {

    // ── Mode ─────────────────────────────────────────────────────────────────

    var isEditMode by mutableStateOf(existingMovie == null)
        private set

    // ── Draft fields ─────────────────────────────────────────────────────────

    var draftTitle by mutableStateOf(existingMovie?.title ?: "")
    var draftYear by mutableStateOf(existingMovie?.year?.toString() ?: "")
    var draftGenre by mutableStateOf(existingMovie?.genre ?: "")
    var draftDescription by mutableStateOf(existingMovie?.description ?: "")
    var draftNotes by mutableStateOf(existingMovie?.notes ?: "")
    var draftTrailerUrl by mutableStateOf(existingMovie?.trailerUrl ?: "")
    var draftPosterUrl by mutableStateOf(existingMovie?.posterUrl ?: "")
    var draftIsWatched by mutableStateOf(existingMovie?.status == WatchStatus.WATCHED)
    var draftRating by mutableStateOf(existingMovie?.rating ?: 0)
    var draftSelectedListIds by mutableStateOf<Set<String>>(
        existingMovie?.listIds?.toSet() ?: emptySet()
    )

    // ── Operation state ───────────────────────────────────────────────────────

    var isSaving by mutableStateOf(false)
        private set
    var isDeleting by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var savedMovieId by mutableStateOf<String?>(existingMovie?.id)
        private set
    var saveSuccess by mutableStateOf(false)
        private set
    var deleteSuccess by mutableStateOf(false)
        private set
    var showDeleteConfirm by mutableStateOf(false)

    // ── Actions ───────────────────────────────────────────────────────────────

    fun enterEditMode() {
        isEditMode = true
    }

    /** Discard unsaved changes and return to view mode. Only valid for existing movies;
     *  for new movies the caller should navigate back instead. */
    fun cancelEdit() {
        val movie = existingMovie ?: return
        draftTitle = movie.title
        draftYear = movie.year?.toString() ?: ""
        draftGenre = movie.genre ?: ""
        draftDescription = movie.description ?: ""
        draftNotes = movie.notes ?: ""
        draftTrailerUrl = movie.trailerUrl ?: ""
        draftPosterUrl = movie.posterUrl ?: ""
        draftIsWatched = movie.status == WatchStatus.WATCHED
        draftRating = movie.rating ?: 0
        draftSelectedListIds = movie.listIds.toSet()
        isEditMode = false
    }

    fun save() {
        val movie = Movie(
            id = existingMovie?.id ?: "",
            title = draftTitle.trim(),
            year = draftYear.toIntOrNull(),
            genre = draftGenre.trim().ifEmpty { null },
            status = if (draftIsWatched) WatchStatus.WATCHED else WatchStatus.WANT_TO_WATCH,
            rating = if (draftIsWatched && draftRating > 0) draftRating else null,
            description = draftDescription.trim().ifEmpty { null },
            notes = draftNotes.trim().ifEmpty { null },
            trailerUrl = draftTrailerUrl.trim().ifEmpty { null },
            posterUrl = draftPosterUrl.trim().ifEmpty { null },
            listIds = draftSelectedListIds.toList(),
            createdAt = existingMovie?.createdAt ?: Timestamp.now()
        )
        isSaving = true
        viewModelScope.launch {
            val result = if (existingMovie == null) {
                movieRepository.addMovie(uid, movie)
            } else {
                movieRepository.updateMovie(uid, movie).map { existingMovie.id }
            }
            isSaving = false
            result
                .onSuccess { newId ->
                    savedMovieId = newId
                    saveSuccess = true
                    isEditMode = false
                }
                .onFailure { errorMessage = it.message ?: "Save failed." }
        }
    }

    fun delete() {
        val id = existingMovie?.id ?: return
        isDeleting = true
        viewModelScope.launch {
            movieRepository.deleteMovie(uid, id)
                .onSuccess { deleteSuccess = true }
                .onFailure { errorMessage = it.message ?: "Delete failed." }
            isDeleting = false
        }
    }

    fun consumeSaveSuccess() { saveSuccess = false }
    fun consumeDeleteSuccess() { deleteSuccess = false }
    fun clearError() { errorMessage = null }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(
            uid: String,
            existingMovie: Movie? = null,
            movieRepository: MovieRepository = FirebaseMovieRepository()
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MovieDetailViewModel(movieRepository, uid, existingMovie) as T
        }
    }
}
