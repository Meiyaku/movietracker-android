package com.ycs.movietracker.ui.detail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.test.FakeTmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [MovieDetailViewModel] — companion test story US-014-T.
 * Parent stories: US-012 (view mode), US-013 (edit existing), US-014 (add new movie).
 *
 * Covers: initial state for new vs existing, mode transitions, save (new + existing),
 * delete, cancelEdit restores original fields, error handling.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
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
        assertTrue(vm.isEditMode.value)
    }

    @Test
    fun existingMovie_startsInViewMode() {
        val vm = makeVm(existingMovie = sampleMovie())
        assertFalse(vm.isEditMode.value)
    }

    @Test
    fun newMovie_draftFieldsAreEmpty() {
        val vm = makeVm(existingMovie = null)
        assertEquals("", vm.draft.value.title)
        assertEquals("", vm.draft.value.year)
        assertEquals("", vm.draft.value.genre)
        assertFalse(vm.draft.value.isWatched)
        assertEquals(0.0, vm.draft.value.rating, 0.001)
    }

    @Test
    fun existingMovie_draftFieldsPrefilledFromMovie() {
        val movie = sampleMovie(title = "Inception", year = 2010, genre = "Sci-Fi", rating = 4.0)
        val vm = makeVm(existingMovie = movie)
        assertEquals("Inception", vm.draft.value.title)
        assertEquals("2010", vm.draft.value.year)
        assertEquals("Sci-Fi", vm.draft.value.genre)
        assertTrue(vm.draft.value.isWatched)
        assertEquals(4.0, vm.draft.value.rating, 0.001)
    }

    // ── Mode transitions ──────────────────────────────────────────────────────

    @Test
    fun enterEditMode_setsIsEditModeTrue() {
        val vm = makeVm(existingMovie = sampleMovie())
        vm.enterEditMode()
        assertTrue(vm.isEditMode.value)
    }

    @Test
    fun cancelEdit_setsIsEditModeFalse() {
        val vm = makeVm(existingMovie = sampleMovie())
        vm.enterEditMode()
        vm.cancelEdit()
        assertFalse(vm.isEditMode.value)
    }

    @Test
    fun cancelEdit_restoresOriginalTitle() {
        val vm = makeVm(existingMovie = sampleMovie(title = "Original"))
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(title = "Modified"))
        vm.cancelEdit()
        assertEquals("Original", vm.draft.value.title)
    }

    @Test
    fun cancelEdit_restoresOriginalYear() {
        val vm = makeVm(existingMovie = sampleMovie(year = 2000))
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(year = "1999"))
        vm.cancelEdit()
        assertEquals("2000", vm.draft.value.year)
    }

    @Test
    fun cancelEdit_restoresOriginalWatchedStatus() {
        val vm = makeVm(existingMovie = sampleMovie())
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(isWatched = false))
        vm.cancelEdit()
        assertTrue(vm.draft.value.isWatched) // original was WATCHED
    }

    @Test
    fun cancelEdit_restoresOriginalRating() {
        val vm = makeVm(existingMovie = sampleMovie(rating = 3.0))
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(rating = 5.0))
        vm.cancelEdit()
        assertEquals(3.0, vm.draft.value.rating, 0.001)
    }

    @Test
    fun cancelEdit_noOpForNewMovie() {
        // cancelEdit should do nothing (no-op) when there is no existing movie;
        // the caller is expected to navigate back instead.
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Draft"))
        vm.cancelEdit() // should not crash; edit mode stays true for new movies
        // Title is unchanged (no restoration since existingMovie == null)
        assertEquals("Draft", vm.draft.value.title)
        assertTrue(vm.isEditMode.value)
    }

    // ── Save – new movie ──────────────────────────────────────────────────────

    @Test
    fun save_newMovie_callsAddMovieOnRepository() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertEquals(1, fakeRepo.addMovieCalls.size)
        assertEquals("Dune", fakeRepo.addMovieCalls.first().title)
    }

    @Test
    fun save_newMovie_setsEditModeToFalseOnSuccess() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertFalse(vm.isEditMode.value)
    }

    @Test
    fun save_newMovie_setsSaveSuccessOnSuccess() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertTrue(vm.operationState.value is DetailOperationState.SaveSuccess)
    }

    @Test
    fun save_newMovie_setsIsSavingFalseAfterCompletion() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertFalse(vm.operationState.value == DetailOperationState.Saving)
    }

    @Test
    fun save_newMovie_storesReturnedIdInSavedMovieId() = runTest {
        fakeRepo.addMovieResultId = "new-id-123"
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertEquals("new-id-123", (vm.operationState.value as DetailOperationState.SaveSuccess).movieId)
    }

    @Test
    fun save_newMovie_ratingNullWhenWantToWatch() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(
            title = "Dune",
            isWatched = false,
            rating = 4.0, // should be ignored
            selectedListIds = setOf("list1")
        ))
        vm.save()
        assertNull(fakeRepo.addMovieCalls.first().rating)
    }

    @Test
    fun save_newMovie_ratingIncludedWhenWatched() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(
            title = "Dune",
            isWatched = true,
            rating = 5.0,
            selectedListIds = setOf("list1")
        ))
        vm.save()
        assertEquals(5.0, fakeRepo.addMovieCalls.first().rating)
    }

    @Test
    fun save_newMovie_failure_setsErrorMessage() = runTest {
        fakeRepo.addMovieFailure = Exception("Network error")
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertEquals("Network error", (vm.operationState.value as DetailOperationState.Error).message)
        assertFalse(vm.operationState.value is DetailOperationState.SaveSuccess)
    }

    @Test
    fun save_newMovie_failure_doesNotSetEditModeFalse() = runTest {
        fakeRepo.addMovieFailure = Exception("Network error")
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", selectedListIds = setOf("list1")))
        vm.save()
        assertTrue(vm.isEditMode.value) // stays in edit mode on failure
    }

    // ── Save – existing movie ─────────────────────────────────────────────────

    @Test
    fun save_existingMovie_callsUpdateMovieOnRepository() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "m1", title = "Original"))
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(title = "Updated"))
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
        assertFalse(vm.isEditMode.value)
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
        assertEquals(DetailOperationState.DeleteSuccess, vm.operationState.value)
    }

    @Test
    fun delete_noOpWhenNoExistingMovie() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.delete()
        assertTrue(fakeRepo.deleteMovieCalls.isEmpty())
        assertFalse(vm.operationState.value == DetailOperationState.DeleteSuccess)
    }

    @Test
    fun delete_failure_setsErrorMessage() = runTest {
        fakeRepo.deleteMovieResult = Result.failure(Exception("Delete failed"))
        val vm = makeVm(existingMovie = sampleMovie(id = "m1"))
        vm.delete()
        assertEquals("Delete failed", (vm.operationState.value as DetailOperationState.Error).message)
        assertFalse(vm.operationState.value == DetailOperationState.DeleteSuccess)
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    @Test
    fun consumeSaveSuccess_clearsSaveSuccess() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Test", selectedListIds = setOf("list1")))
        vm.save()
        assertTrue(vm.operationState.value is DetailOperationState.SaveSuccess)
        vm.resetOperationState()
        assertEquals(DetailOperationState.Idle, vm.operationState.value)
    }

    @Test
    fun clearError_clearsErrorMessage() = runTest {
        fakeRepo.addMovieFailure = Exception("err")
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Test", selectedListIds = setOf("list1")))
        vm.save()
        assertEquals("err", (vm.operationState.value as DetailOperationState.Error).message)
        vm.resetOperationState()
        assertEquals(DetailOperationState.Idle, vm.operationState.value)
    }

    // ── Poster URL field ──────────────────────────────────────────────────────

    @Test
    fun existingMovie_draftPosterUrlPrefilledFromMovie() {
        val movie = sampleMovie().copy(posterUrl = "https://image.tmdb.org/t/p/w500/abc.jpg")
        val vm = makeVm(existingMovie = movie)
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", vm.draft.value.posterUrl)
    }

    @Test
    fun newMovie_draftPosterUrlIsEmpty() {
        val vm = makeVm(existingMovie = null)
        assertEquals("", vm.draft.value.posterUrl)
    }

    @Test
    fun cancelEdit_restoresOriginalPosterUrl() {
        val movie = sampleMovie().copy(posterUrl = "https://image.tmdb.org/t/p/w500/original.jpg")
        val vm = makeVm(existingMovie = movie)
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(posterUrl = "https://image.tmdb.org/t/p/w500/changed.jpg"))
        vm.cancelEdit()
        assertEquals("https://image.tmdb.org/t/p/w500/original.jpg", vm.draft.value.posterUrl)
    }

    @Test
    fun save_newMovie_includesPosterUrlInSavedMovie() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(
            title = "Dune",
            posterUrl = "https://image.tmdb.org/t/p/w500/dune.jpg",
            selectedListIds = setOf("list1")
        ))
        vm.save()
        assertEquals("https://image.tmdb.org/t/p/w500/dune.jpg", fakeRepo.addMovieCalls.first().posterUrl)
    }

    @Test
    fun save_existingMovie_includesPosterUrlInUpdatedMovie() = runTest {
        val vm = makeVm(existingMovie = sampleMovie(id = "m1"))
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(posterUrl = "https://image.tmdb.org/t/p/w500/new.jpg"))
        vm.save()
        assertEquals("https://image.tmdb.org/t/p/w500/new.jpg", fakeRepo.updateMovieCalls.first().posterUrl)
    }

    @Test
    fun save_newMovie_posterUrlNullWhenEmpty() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "Dune", posterUrl = "", selectedListIds = setOf("list1")))
        vm.save()
        assertNull(fakeRepo.addMovieCalls.first().posterUrl)
    }

    // ── Real-time validation ──────────────────────────────────────────────────

    @Test
    fun updateDraft_titleTouchedThenCleared_showsTitleErrorImmediately() {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "A"))  // touch
        vm.updateDraft(vm.draft.value.copy(title = ""))   // clear → invalid
        assertNotNull(vm.draftErrors.value.title)
    }

    @Test
    fun updateDraft_titleFixed_clearsTitleErrorImmediately() {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(title = "A"))
        vm.updateDraft(vm.draft.value.copy(title = ""))
        assertNotNull(vm.draftErrors.value.title)
        vm.updateDraft(vm.draft.value.copy(title = "Inception"))
        assertNull(vm.draftErrors.value.title)
    }

    @Test
    fun updateDraft_invalidYear_showsYearErrorImmediately() {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(year = "1800")) // below MIN_MOVIE_YEAR
        assertNotNull(vm.draftErrors.value.year)
    }

    @Test
    fun updateDraft_validYear_clearsYearErrorImmediately() {
        val vm = makeVm(existingMovie = null)
        vm.updateDraft(vm.draft.value.copy(year = "1800"))
        assertNotNull(vm.draftErrors.value.year)
        vm.updateDraft(vm.draft.value.copy(year = "2020"))
        assertNull(vm.draftErrors.value.year)
    }

    @Test
    fun updateDraft_untouchedTitleField_noErrorBeforeSave() {
        val vm = makeVm(existingMovie = null)
        // Only touch year — title (empty) must stay error-free
        vm.updateDraft(vm.draft.value.copy(year = "2020"))
        assertNull(vm.draftErrors.value.title)
    }

    @Test
    fun save_withErrors_marksErroredFieldsForRealTimeValidation() = runTest {
        val vm = makeVm(existingMovie = null)
        vm.save() // title empty → fails
        assertTrue(vm.draftErrors.value.hasErrors)
        // After failed save, fixing title must clear the error without another save attempt
        vm.updateDraft(vm.draft.value.copy(title = "Inception"))
        assertNull(vm.draftErrors.value.title)
    }

    @Test
    fun cancelEdit_clearsTouchedState_soReEditStartsClean() {
        val vm = makeVm(existingMovie = sampleMovie(title = "Original"))
        vm.enterEditMode()
        vm.updateDraft(vm.draft.value.copy(title = "A"))
        vm.updateDraft(vm.draft.value.copy(title = "")) // title error visible
        assertNotNull(vm.draftErrors.value.title)
        vm.cancelEdit()
        vm.enterEditMode()
        // Title was restored to "Original" and touched state was cleared — no error
        assertNull(vm.draftErrors.value.title)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val fakeRemoteConfig = object : RemoteConfigRepository {
        override val pageSize = 50
        override val maxRetryAttempts = 3
        override val isTmdbSearchEnabled = MutableStateFlow(true)
        override val tmdbApiKey = MutableStateFlow("")
    }

    private val fakeTmdbRepo = FakeTmdbRepository()

    private fun makeVm(existingMovie: Movie?) =
        MovieDetailViewModel(fakeRepo, fakeRemoteConfig, fakeTmdbRepo, context, uid = "user1", movieId = existingMovie?.id ?: "new", existingMovie = existingMovie)

    private fun sampleMovie(
        id: String = "m1",
        title: String = "Sample",
        year: Int? = 2020,
        genre: String? = "Action",
        rating: Double? = 4.0,
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
    val addMovieCalls = mutableListOf<NewMovie>()
    val updateMovieCalls = mutableListOf<Movie>()
    val deleteMovieCalls = mutableListOf<String>()

    var addMovieResultId: String = "generated-id"
    var addMovieFailure: Exception? = null
    var updateMovieResult: Result<Unit> = Result.success(Unit)
    var deleteMovieResult: Result<Unit> = Result.success(Unit)

    override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?): Result<MoviesPage> =
        Result.success(MoviesPage(emptyList(), null, false))

    override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> {
        addMovieCalls.add(movie)
        val failure = addMovieFailure
        return if (failure != null) Result.failure(failure)
               else Result.success(movie.toMovie(id = addMovieResultId))
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

    var checkDuplicateResult: Result<Boolean> = Result.success(false)
    override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
        Result.failure(UnsupportedOperationException())

    override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?): Result<Boolean> =
        checkDuplicateResult
    override suspend fun deleteAllMovies(uid: String): Result<Unit> = Result.success(Unit)
}
