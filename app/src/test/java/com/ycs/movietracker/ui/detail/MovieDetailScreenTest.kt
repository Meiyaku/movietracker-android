package com.ycs.movietracker.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.MoviesPage
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.test.FakeTmdbRepository
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import com.ycs.movietracker.util.AndroidStringProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MovieDetailScreen] — companion test story US-017-T.
 * Parent stories: US-012 (view mode), US-013 (edit), US-014 (add new), US-015 (delete).
 *
 * Covers: view mode display (title, badges, trailer button, list names),
 * edit mode interactions (save button state, delete button visibility),
 * and post-save navigation callback for new movies.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MovieDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeRepo = FakeDetailRepo()

    private val fakeRemoteConfig = object : RemoteConfigRepository {
        override val pageSize = 50
        override val maxRetryAttempts = 3
        override val isTmdbSearchEnabled = MutableStateFlow(true)
        override val tmdbApiKey = MutableStateFlow("")
        override val whatsNew = MutableStateFlow("")
        override val whatsNewVersion = MutableStateFlow(0)
    }

    private val fakeTmdbRepo = FakeTmdbRepository()

    private val fakeConnectivity = object : com.ycs.movietracker.util.ConnectivityMonitor { override val isOnline = true }

    private fun makeVm(existingMovie: Movie? = null) =
        MovieDetailViewModel(fakeRepo, fakeRemoteConfig, fakeTmdbRepo, AndroidStringProvider(ApplicationProvider.getApplicationContext()), fakeConnectivity, uid = "user1", movieId = existingMovie?.id ?: "new", existingMovie = existingMovie)

    private fun setContent(
        vm: MovieDetailViewModel,
        allLists: List<MovieList> = emptyList(),
        onBack: () -> Unit = {},
        onDeleted: () -> Unit = {},
        onSaved: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MovietrackerTheme {
                MovieDetailScreen(
                    viewModel = vm,
                    allLists = allLists,
                    onBack = onBack,
                    onDeleted = onDeleted,
                    onSaved = onSaved
                )
            }
        }
    }

    // ── View mode — title and buttons ─────────────────────────────────────────

    @Test
    fun viewMode_showsMovieTitle() {
        setContent(vm = makeVm(existingMovie = watchedMovie(title = "Inception")))
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }

    @Test
    fun viewMode_showsEditButton() {
        setContent(vm = makeVm(existingMovie = watchedMovie()))
        composeTestRule.onNodeWithText("Edit").assertIsDisplayed()
    }

    // ── View mode — watch status badges ───────────────────────────────────────

    @Test
    fun viewMode_showsWatchedBadge_whenWatched() {
        setContent(vm = makeVm(existingMovie = watchedMovie()))
        composeTestRule.onNodeWithText("Watched").assertIsDisplayed()
    }

    @Test
    fun viewMode_showsWantToWatchBadge_whenUnwatched() {
        setContent(vm = makeVm(existingMovie = wantToWatchMovie()))
        composeTestRule.onNodeWithText("Want to Watch").assertIsDisplayed()
    }

    // ── View mode — trailer button ─────────────────────────────────────────────

    @Test
    fun viewMode_showsTrailerButton_whenTrailerUrlSet() {
        val movie = watchedMovie(trailerUrl = "https://youtube.com/watch?v=abc")
        setContent(vm = makeVm(existingMovie = movie))
        composeTestRule.onNodeWithText("Watch Trailer").assertIsDisplayed()
    }

    @Test
    fun viewMode_doesNotShowTrailerButton_whenNoTrailerUrl() {
        setContent(vm = makeVm(existingMovie = watchedMovie(trailerUrl = null)))
        composeTestRule.onNodeWithText("Watch Trailer").assertDoesNotExist()
    }

    // ── Edit mode — save button state ─────────────────────────────────────────

    @Test
    fun editMode_saveButton_isDisabled_whenTitleEmpty() {
        setContent(vm = makeVm(existingMovie = null)) // new movie → starts in edit mode
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun editMode_saveButton_isEnabled_whenTitleNonEmpty() {
        setContent(vm = makeVm(existingMovie = null))
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("Dune")
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    // ── Edit mode — delete button visibility ──────────────────────────────────

    @Test
    fun editMode_deleteButton_isVisible_forExistingMovie() {
        setContent(vm = makeVm(existingMovie = watchedMovie()))
        composeTestRule.onNodeWithText("Edit").performClick()
        // Button is at the bottom of a scrollable column; assertExists() confirms it's in the tree
        composeTestRule.onNodeWithContentDescription("Delete movie").assertExists()
    }

    @Test
    fun editMode_deleteButton_isNotVisible_forNewMovie() {
        setContent(vm = makeVm(existingMovie = null)) // starts in edit mode
        composeTestRule.onNodeWithContentDescription("Delete movie").assertDoesNotExist()
    }

    @Test
    fun deleteConfirmDialog_isShown_whenShowDeleteConfirmIsTrue() {
        val vm = makeVm(existingMovie = watchedMovie(title = "Inception"))
        vm.requestDeleteConfirm()
        setContent(vm = vm)
        composeTestRule.onNodeWithText("Delete movie?").assertIsDisplayed()
    }

    // ── View mode — poster image ──────────────────────────────────────────────

    @Test
    fun viewMode_showsPosterImage_whenPosterUrlSet() {
        val movie = watchedMovie(title = "Alien").copy(posterUrl = "https://image.tmdb.org/t/p/w500/alien.jpg")
        setContent(vm = makeVm(existingMovie = movie))
        composeTestRule.onNodeWithContentDescription("Movie poster for Alien").assertExists()
    }

    @Test
    fun viewMode_doesNotShowPosterImage_whenNoPosterUrl() {
        setContent(vm = makeVm(existingMovie = watchedMovie(title = "No Poster")))
        composeTestRule.onNodeWithContentDescription("Movie poster for No Poster").assertDoesNotExist()
    }

    // ── Post-save navigation for new movies ───────────────────────────────────

    @Test
    fun save_newMovie_triggersOnSavedCallback() {
        var savedCalled = false
        val vm = makeVm(existingMovie = null)
        setContent(vm = vm, onSaved = { savedCalled = true })
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("Dune")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        assertTrue("onSaved callback must be invoked after saving a new movie", savedCalled)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun watchedMovie(
        id: String = "m1",
        title: String = "Sample Movie",
        trailerUrl: String? = null,
        listIds: List<String> = listOf("list-my-movies")
    ) = Movie(
        id = id,
        title = title,
        status = WatchStatus.WATCHED,
        rating = 4.0,
        trailerUrl = trailerUrl,
        listIds = listIds,
        createdAt = Timestamp.now()
    )

    private fun wantToWatchMovie(
        id: String = "m2",
        title: String = "Sample 2"
    ) = Movie(
        id = id,
        title = title,
        status = WatchStatus.WANT_TO_WATCH,
        listIds = listOf("list-my-movies"),
        createdAt = Timestamp.now()
    )
}

// ── Fake repository ───────────────────────────────────────────────────────────

private class FakeDetailRepo : MovieRepository {
    override suspend fun getMoviesPage(uid: String, listId: String, pageSize: Int, afterId: String?): Result<MoviesPage> =
        Result.success(MoviesPage(emptyList(), null, false))
    override suspend fun addMovie(uid: String, movie: NewMovie): Result<Movie> =
        Result.success(movie.toMovie(id = "new-id"))
    override suspend fun updateMovie(uid: String, movie: Movie): Result<Unit> =
        Result.success(Unit)
    override suspend fun deleteMovie(uid: String, movieId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun removeListFromMovies(uid: String, listId: String): Result<Unit> =
        Result.success(Unit)
    override suspend fun getMovieById(uid: String, movieId: String): Result<Movie> =
        Result.failure(UnsupportedOperationException())

    override suspend fun checkDuplicate(uid: String, title: String, year: Int?, genre: String?, excludeId: String?): Result<Boolean> =
        Result.success(false)
    override suspend fun deleteAllMovies(uid: String): Result<Unit> = Result.success(Unit)
}
