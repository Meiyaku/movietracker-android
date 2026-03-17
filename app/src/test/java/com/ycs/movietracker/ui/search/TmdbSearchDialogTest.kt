package com.ycs.movietracker.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.TmdbSearchResult
import com.ycs.movietracker.data.repository.TmdbRepository
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [TmdbSearchDialog].
 *
 * Uses a [FakeTmdbRepo] to avoid real network calls. Covers: dismiss, search
 * triggering, result display, error display, and result selection (including
 * trailer URL pass-through).
 *
 * Run with: ./gradlew test
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TmdbSearchDialogTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // ── Dismiss ───────────────────────────────────────────────────────────────

    @Test
    fun cancelButton_callsOnDismiss() {
        var dismissed = false
        setContent(onDismiss = { dismissed = true })
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    // ── Search triggering ─────────────────────────────────────────────────────

    @Test
    fun emptyQuery_doesNotCallRepository() {
        val fake = FakeTmdbRepo()
        setContent(repository = fake)
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, fake.searchCallCount)
    }

    @Test
    fun nonEmptyQuery_callsRepositoryWithEnteredText() {
        val fake = FakeTmdbRepo()
        setContent(repository = fake)
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Dune")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, fake.searchCallCount)
        assertEquals("Dune", fake.lastSearchQuery)
    }

    // ── Result display ────────────────────────────────────────────────────────

    @Test
    fun successfulSearch_displaysResultTitle() {
        val fake = FakeTmdbRepo(searchResult = Result.success(listOf(searchResult(title = "Dune: Part Two"))))
        setContent(repository = fake)
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Dune")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dune: Part Two").assertIsDisplayed()
    }

    @Test
    fun failedSearch_displaysErrorMessage() {
        val fake = FakeTmdbRepo(searchResult = Result.failure(Exception("Connection timeout")))
        setContent(repository = fake)
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Dune")
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Connection timeout").assertIsDisplayed()
    }

    // ── Result selection ──────────────────────────────────────────────────────

    @Test
    fun tappingResult_callsOnResultWithCorrectSearchResult() {
        val expected = searchResult(id = 42, title = "Inception")
        val fake = FakeTmdbRepo(searchResult = Result.success(listOf(expected)))
        var received: TmdbSearchResult? = null
        setContent(repository = fake, onResult = { r, _ -> received = r })
        search(fake, "Inception")
        clickResult("Inception")
        assertEquals(expected, received)
    }

    @Test
    fun tappingResult_callsOnResultWithTrailerUrl_whenTrailerFound() {
        val fake = FakeTmdbRepo(
            searchResult = Result.success(listOf(searchResult(title = "Oppenheimer"))),
            trailerResult = Result.success("https://www.youtube.com/watch?v=uYPbbksJxIg")
        )
        var receivedUrl: String? = null
        setContent(repository = fake, onResult = { _, url -> receivedUrl = url })
        search(fake, "Oppenheimer")
        clickResult("Oppenheimer")
        assertEquals("https://www.youtube.com/watch?v=uYPbbksJxIg", receivedUrl)
    }

    @Test
    fun tappingResult_callsOnResultWithNullTrailerUrl_whenNoTrailerFound() {
        val fake = FakeTmdbRepo(
            searchResult = Result.success(listOf(searchResult(title = "Silent Film"))),
            trailerResult = Result.success(null)
        )
        var receivedUrl: String? = "sentinel"
        setContent(repository = fake, onResult = { _, url -> receivedUrl = url })
        search(fake, "Silent Film")
        clickResult("Silent Film")
        assertNull(receivedUrl)
    }

    @Test
    fun tappingResult_fetchesTrailerForCorrectMovieId() {
        val fake = FakeTmdbRepo(searchResult = Result.success(listOf(searchResult(id = 99, title = "Alien"))))
        setContent(repository = fake)
        search(fake, "Alien")
        clickResult("Alien")
        assertEquals(99, fake.lastTrailerMovieId)
    }

    @Test
    fun tappingResult_callsOnDismiss() {
        val fake = FakeTmdbRepo(searchResult = Result.success(listOf(searchResult(title = "Gravity"))))
        var dismissed = false
        setContent(repository = fake, onDismiss = { dismissed = true })
        search(fake, "Gravity")
        clickResult("Gravity")
        assertTrue(dismissed)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setContent(
        repository: TmdbRepository = FakeTmdbRepo(),
        onDismiss: () -> Unit = {},
        onResult: (TmdbSearchResult, String?) -> Unit = { _, _ -> }
    ) {
        composeTestRule.setContent {
            MovietrackerTheme {
                TmdbSearchDialog(
                    repository = repository,
                    onDismiss = onDismiss,
                    onResult = onResult
                )
            }
        }
    }

    /** Types [query] into the search field and clicks the search icon. */
    private fun search(fake: FakeTmdbRepo, query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * Clicks the result row that contains [title].
     *
     * After a search, the typed query also sits in the search text field, so
     * [onNodeWithText] would match two nodes (the field AND the result row).
     * Filtering by `hasSetTextAction().not()` isolates the result row, which
     * has no text-editing semantics.
     */
    private fun clickResult(title: String) {
        composeTestRule
            .onAllNodes(hasText(title, substring = true))
            .filterToOne(hasSetTextAction().not())
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun searchResult(
        id: Int = 1,
        title: String = "Test Movie",
        releaseDate: String = "2024-01-01"
    ) = TmdbSearchResult(
        id = id,
        title = title,
        releaseDate = releaseDate,
        overview = "An overview.",
        posterPath = null,
        voteAverage = 7.5
    )
}

// ── Fake repository ───────────────────────────────────────────────────────────

private class FakeTmdbRepo(
    private val searchResult: Result<List<TmdbSearchResult>> = Result.success(emptyList()),
    private val trailerResult: Result<String?> = Result.success(null)
) : TmdbRepository {

    var searchCallCount = 0
    var lastSearchQuery: String? = null
    var lastTrailerMovieId: Int? = null

    override suspend fun searchMovies(query: String): Result<List<TmdbSearchResult>> {
        searchCallCount++
        lastSearchQuery = query
        return searchResult
    }

    override suspend fun getTrailerUrl(movieId: Int): Result<String?> {
        lastTrailerMovieId = movieId
        return trailerResult
    }
}
