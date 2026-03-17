package com.ycs.movietracker.ui.detail

import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MovieDetailViewModel] — companion test story US-014-T.
 * Parent stories: US-012 (view mode), US-013 (edit existing), US-014 (add new movie).
 *
 * Covers: initial state for new vs existing, mode transitions, save (new + existing),
 * delete, cancelEdit restores original fields, error handling.
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeMovieRepo

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeMovieRepo()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun newMovie_startsInEditMode() {
        val vm = makeVm(existingMovie = null)
        assertTrue(vm.isEditMode)
    }

    @Test
    fun existingMovie_startsInViewMode() {
        val vm = makeVm(existingMovie = sampleMovie())
        assertFalse(vm.isEditMode)
    }

    @Test
    fun newMovie_draftFieldsAreEmpty() {
        val vm = makeVm(existingMovie = null)
        assertEquals("", vm.draftTitle)
        assertEquals("", vm.draftYear)
        assertEquals("", vm.draftGenre)
        assertFalse(vm.draftIsWatched)
        assertEquals(0, vm.draftRating)
    }

    @Test
    fun existingMovie_draftFieldsPrefilledFromMovie() {
        val movie = sampleMovie(title = "Inception", year = 2010, genre = "Sci-Fi", rating = 4)
        val vm = makeVm(existingMovie = movie)
        assertEquals("Inception", vm.draftTitle)
        assertEquals("2010", vm.draftYear)
        assertEquals("Sci-Fi", vm.draftGenre)
        assertTrue(vm.draftIsWatched)
        assertEquals(4, vm.draftRating)
    }

    // ── Mode transitions ──────────────────────────────────────────────────────

    @Test
    fun enterEditMode_setsIsEditModeTrue() {
        val vm = makeVm(existingMovie = sampleMovie())
        vm.enterEditMode()
        assertTrue(vm.isEditMode)
    }

    @Test
    fun cancelEdit_setsIsEditModeFalse() {
        val vm = makeVm(existingMovie = sampleMovie())
        vm.enterEditMode()
        vm.cancelEdit()
        assertFalse(vm.isEditMode)
    }

    @Test
    fun cancelEdit_restoresOriginalTitle() {
        val vm = makeVm(existingMovie = sampleMovie(title = "Original"))
        vm.enterEditMode()
        vm.draftTitle = "Modified"
        vm.cancelEdit()
        assertEquals("Original", vm.draftTitle)
    }

    @Test
    fun cancelEdit_restoresOriginalYear() {
        val vm = makeVm(existingMovie = sampleMovie(year = 2000))
        vm.enterEditMode()
        vm.draftYear = "1999"
        vm.cancelEdit()
        assertEquals("2000", vm.draftYear)
    }

    @Test
    fun cancelEdit_restoresOriginalWatchedStatus() {
        val vm = makeVm(existingMovie = sampleMovie())
        vm.enterEditMode()
        vm.draftIsWatched = false
        vm.cancelEdit()
        assertTrue(vm.draftIsWatched) // original was WATCHED
    }

    @Test
    fun cancelEdit_restoresOriginalRating() {
        val vm = makeVm(existingMovie = sampleMovie(rating = 3))
        vm.enterEditMode()
        vm.draftRating = 5
        vm.cancelEdit()
        assertEquals(3, vm.draftRating)
    }

    @Test
    fun cancelEdit_noOpForNewMovie() {
        // cancelEdit should do nothing (no-op) when there is no existing movie;
        // the caller is expected to navigate back instead.
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Draft"
        vm.cancelEdit() // should not crash; edit mode stays true for new movies
        // Title is unchanged (no restoration since existingMovie == null)
        assertEquals("Draft", vm.draftTitle)
        assertTrue(vm.isEditMode)
    }

    // ── Save – new movie ──────────────────────────────────────────────────────

    @Test
    fun save_newMovie_callsAddMovieOnRepository() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertEquals(1, fakeRepo.addMovieCalls.size)
        assertEquals("Dune", fakeRepo.addMovieCalls.first().title)
    }

    @Test
    fun save_newMovie_setsEditModeToFalseOnSuccess() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertFalse(vm.isEditMode)
    }

    @Test
    fun save_newMovie_setsSaveSuccessOnSuccess() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertTrue(vm.saveSuccess)
    }

    @Test
    fun save_newMovie_setsIsSavingFalseAfterCompletion() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertFalse(vm.isSaving)
    }

    @Test
    fun save_newMovie_storesReturnedIdInSavedMovieId() = runTest {
        fakeRepo.addMovieResult = Result.success("new-id-123")
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertEquals("new-id-123", vm.savedMovieId)
    }

    @Test
    fun save_newMovie_ratingNullWhenWantToWatch() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftIsWatched = false
        vm.draftRating = 4 // should be ignored
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertNull(fakeRepo.addMovieCalls.first().rating)
    }

    @Test
    fun save_newMovie_ratingIncludedWhenWatched() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftIsWatched = true
        vm.draftRating = 5
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertEquals(5, fakeRepo.addMovieCalls.first().rating)
    }

    @Test
    fun save_newMovie_failure_setsErrorMessage() = runTest {
        fakeRepo.addMovieResult = Result.failure(Exception("Network error"))
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertEquals("Network error", vm.errorMessage)
        assertFalse(vm.saveSuccess)
    }

    @Test
    fun save_newMovie_failure_doesNotSetEditModeFalse() = runTest {
        fakeRepo.addMovieResult = Result.failure(Exception("Network error"))
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertTrue(vm.isEditMode) // stays in edit mode on failure
    }

    // ── Save – existing movie ─────────────────────────────────────────────────

    @Test
    fun save_existingMovie_callsUpdateMovieOnRepository() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "m1", title = "Original"))
        vm.enterEditMode()
        vm.draftTitle = "Updated"
        vm.save()
        assertEquals(1, fakeRepo.updateMovieCalls.size)
        assertEquals("Updated", fakeRepo.updateMovieCalls.first().title)
        assertEquals(0, fakeRepo.addMovieCalls.size)
    }

    @Test
    fun save_existingMovie_setsEditModeToFalseOnSuccess() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "m1"))
        vm.enterEditMode()
        vm.save()
        assertFalse(vm.isEditMode)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun delete_callsDeleteMovieWithCorrectId() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "del-id"))
        vm.delete()
        assertEquals(listOf("del-id"), fakeRepo.deleteMovieCalls)
    }

    @Test
    fun delete_setsDeleteSuccessOnSuccess() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "del-id"))
        vm.delete()
        assertTrue(vm.deleteSuccess)
    }

    @Test
    fun delete_noOpWhenNoExistingMovie() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.delete()
        assertTrue(fakeRepo.deleteMovieCalls.isEmpty())
        assertFalse(vm.deleteSuccess)
    }

    @Test
    fun delete_failure_setsErrorMessage() = runTest {
        fakeRepo.deleteMovieResult = Result.failure(Exception("Delete failed"))
        val vm = makeVm(existingMovie = sampleMovie(id = "m1"))
        vm.delete()
        assertEquals("Delete failed", vm.errorMessage)
        assertFalse(vm.deleteSuccess)
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    @Test
    fun consumeSaveSuccess_clearsSaveSuccess() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Test"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertTrue(vm.saveSuccess)
        vm.consumeSaveSuccess()
        assertFalse(vm.saveSuccess)
    }

    @Test
    fun clearError_clearsErrorMessage() = runTest {
        fakeRepo.addMovieResult = Result.failure(Exception("err"))
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Test"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertEquals("err", vm.errorMessage)
        vm.clearError()
        assertNull(vm.errorMessage)
    }

    // ── Poster URL field ──────────────────────────────────────────────────────

    @Test
    fun existingMovie_draftPosterUrlPrefilledFromMovie() {
        val movie = sampleMovie().copy(posterUrl = "https://image.tmdb.org/t/p/w500/abc.jpg")
        val vm = makeVm(existingMovie = movie)
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", vm.draftPosterUrl)
    }

    @Test
    fun newMovie_draftPosterUrlIsEmpty() {
        val vm = makeVm(existingMovie = null)
        assertEquals("", vm.draftPosterUrl)
    }

    @Test
    fun cancelEdit_restoresOriginalPosterUrl() {
        val movie = sampleMovie().copy(posterUrl = "https://image.tmdb.org/t/p/w500/original.jpg")
        val vm = makeVm(existingMovie = movie)
        vm.enterEditMode()
        vm.draftPosterUrl = "https://image.tmdb.org/t/p/w500/changed.jpg"
        vm.cancelEdit()
        assertEquals("https://image.tmdb.org/t/p/w500/original.jpg", vm.draftPosterUrl)
    }

    @Test
    fun save_newMovie_includesPosterUrlInSavedMovie() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftPosterUrl = "https://image.tmdb.org/t/p/w500/dune.jpg"
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertEquals("https://image.tmdb.org/t/p/w500/dune.jpg", fakeRepo.addMovieCalls.first().posterUrl)
    }

    @Test
    fun save_existingMovie_includesPosterUrlInUpdatedMovie() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "m1"))
        vm.enterEditMode()
        vm.draftPosterUrl = "https://image.tmdb.org/t/p/w500/new.jpg"
        vm.save()
        assertEquals("https://image.tmdb.org/t/p/w500/new.jpg", fakeRepo.updateMovieCalls.first().posterUrl)
    }

    @Test
    fun save_newMovie_posterUrlNullWhenEmpty() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.draftTitle = "Dune"
        vm.draftPosterUrl = ""
        vm.draftSelectedListIds = setOf("list1")
        vm.save()
        assertNull(fakeRepo.addMovieCalls.first().posterUrl)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeVm(existingMovie: Movie?) =
        MovieDetailViewModel(fakeRepo, uid = "user1", existingMovie = existingMovie)

    private fun sampleMovie(
        id: String = "m1",
        title: String = "Sample",
        year: Int? = 2020,
        genre: String? = "Action",
        rating: Int? = 4,
        listIds: List<String> = listOf("list1")
    ) = Movie(
        id = id,
        title = title,
        year = year,
        genre = genre,
        status = WatchStatus.WATCHED,
        rating = rating,
        listIds = listIds,
        createdAt = Timestamp.now()
    )
}

// ── Fake repository ───────────────────────────────────────────────────────────

private class FakeMovieRepo : MovieRepository {
    val addMovieCalls = mutableListOf<Movie>()
    val updateMovieCalls = mutableListOf<Movie>()
    val deleteMovieCalls = mutableListOf<String>()

    var addMovieResult: Result<String> = Result.success("generated-id")
    var updateMovieResult: Result<Unit> = Result.success(Unit)
    var deleteMovieResult: Result<Unit> = Result.success(Unit)

    override fun getMoviesForList(uid: String, listId: String): Flow<List<Movie>> = emptyFlow()

    override suspend fun addMovie(uid: String, movie: Movie): Result<String> {
        addMovieCalls.add(movie)
        return addMovieResult
    }

    override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> {
        updateMovieCalls.add(movie)
        return updateMovieResult
    }

    override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> {
        deleteMovieCalls.add(movieId)
        return deleteMovieResult
    }

    override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
        Result.success(Unit)
}
