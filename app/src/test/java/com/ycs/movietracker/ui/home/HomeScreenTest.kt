package com.ycs.movietracker.ui.home

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.MovieList
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.ui.theme.MovietrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [HomeScreen] — companion test story US-020-T.
 *
 * Covers: empty states, loading indicator, movie card display and click,
 * FAB, search bar (placeholder, clear button), top-bar content (list name,
 * status summary, menu button), sort/filter dropdown, snackbar, and basic
 * drawer opening via the menu button.
 *
 * [AppDrawerContentTest] covers the drawer internals exhaustively.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun movie(
        title: String,
        id: String = title,
        status: WatchStatus = WatchStatus.WANT_TO_WATCH,
        year: Int? = null
    ) = Movie(id = id, title = title, status = status, year = year)

    private fun setScreen(
        movies: List<Movie> = emptyList(),
        lists: List<MovieList> = emptyList(),
        activeList: MovieList? = null,
        searchQuery: String = "",
        sortOrder: SortOrder = SortOrder.TITLE_ASC,
        watchFilter: WatchFilter = WatchFilter.ALL,
        isLoadingMovies: Boolean = false,
        isLoadingLists: Boolean = false,
        hasMoreMovies: Boolean = false,
        isLoadingMore: Boolean = false,
        snackbarMessage: String? = null,
        onSearchQueryChange: (String) -> Unit = {},
        onSortOrderChange: (SortOrder) -> Unit = {},
        onWatchFilterChange: (WatchFilter) -> Unit = {},
        onMovieClick: (Movie) -> Unit = {},
        onAddMovieClick: () -> Unit = {},
        onLogOut: () -> Unit = {},
        onSettings: () -> Unit = {},
        onListSelected: (MovieList) -> Unit = {},
        onSnackbarDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MovietrackerTheme {
                HomeScreen(
                    movies = movies,
                    lists = lists,
                    activeList = activeList,
                    searchQuery = searchQuery,
                    sortOrder = sortOrder,
                    watchFilter = watchFilter,
                    isLoadingMovies = isLoadingMovies,
                    isLoadingLists = isLoadingLists,
                    hasMoreMovies = hasMoreMovies,
                    isLoadingMore = isLoadingMore,
                    snackbarMessage = snackbarMessage,
                    onSearchQueryChange = onSearchQueryChange,
                    onSortOrderChange = onSortOrderChange,
                    onWatchFilterChange = onWatchFilterChange,
                    onMovieClick = onMovieClick,
                    onAddMovieClick = onAddMovieClick,
                    onLogOut = onLogOut,
                    onSettings = onSettings,
                    onListSelected = onListSelected,
                    onSnackbarDismiss = onSnackbarDismiss
                )
            }
        }
    }

    /** Asserts no node matching [matcher] exists in the merged semantics tree. */
    private fun assertAbsent(matcher: SemanticsMatcher) {
        assertTrue(composeTestRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty())
    }

    // ── Empty states ──────────────────────────────────────────────────────────

    @Test
    fun emptyState_noMovies_showsDefaultMessage() {
        setScreen()
        composeTestRule.onNodeWithText("No movies yet. Add one!").assertIsDisplayed()
    }

    @Test
    fun emptyState_withActiveSearch_showsSearchMessage() {
        setScreen(searchQuery = "xyzzy")
        composeTestRule.onNodeWithText("No movies match your search.").assertIsDisplayed()
    }

    @Test
    fun emptyState_withActiveFilter_showsFilterMessage() {
        setScreen(watchFilter = WatchFilter.WATCHED)
        composeTestRule.onNodeWithText("No movies match the current filter.").assertIsDisplayed()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    @Test
    fun loading_isLoadingMovies_showsProgressIndicator() {
        setScreen(isLoadingMovies = true)
        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun loading_isLoadingLists_showsProgressIndicator() {
        setScreen(isLoadingLists = true)
        // isLoadingLists = true renders spinners in both the drawer and the main content;
        // assert that at least one is displayed rather than requiring a unique match.
        composeTestRule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun loading_isLoadingMovies_suppressesEmptyState() {
        setScreen(isLoadingMovies = true)
        assertAbsent(hasText("No movies yet. Add one!"))
    }

    // ── Movie cards ───────────────────────────────────────────────────────────

    @Test
    fun movieList_showsAllTitles() {
        setScreen(movies = listOf(movie("Inception"), movie("The Matrix")))
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Matrix").assertIsDisplayed()
    }

    @Test
    fun movieList_showsYear_whenPresent() {
        setScreen(movies = listOf(movie("Inception", year = 2010)))
        composeTestRule.onNodeWithText("2010").assertIsDisplayed()
    }

    @Test
    fun movieCard_click_invokesOnMovieClickWithCorrectMovie() {
        val inception = movie("Inception")
        var clicked: Movie? = null
        setScreen(movies = listOf(inception), onMovieClick = { clicked = it })
        composeTestRule.onNodeWithText("Inception").performClick()
        assertEquals(inception, clicked)
    }

    // ── FAB ───────────────────────────────────────────────────────────────────

    @Test
    fun fab_isDisplayed() {
        setScreen()
        composeTestRule.onNodeWithContentDescription("Add movie").assertIsDisplayed()
    }

    @Test
    fun fab_click_invokesOnAddMovieClick() {
        var clicked = false
        setScreen(onAddMovieClick = { clicked = true })
        composeTestRule.onNodeWithContentDescription("Add movie").performClick()
        assertTrue(clicked)
    }

    // ── Search bar ────────────────────────────────────────────────────────────

    @Test
    fun searchBar_showsPlaceholder_whenQueryEmpty() {
        setScreen(searchQuery = "")
        composeTestRule.onNodeWithText("Search movies & shows…").assertIsDisplayed()
    }

    @Test
    fun searchBar_clearButton_visibleWhenQueryNonEmpty() {
        setScreen(searchQuery = "matrix")
        composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test
    fun searchBar_clearButton_absentWhenQueryEmpty() {
        setScreen(searchQuery = "")
        assertAbsent(hasContentDescription("Clear search"))
    }

    @Test
    fun searchBar_clearButton_click_emitsEmptyString() {
        var emitted: String? = null
        setScreen(searchQuery = "matrix", onSearchQueryChange = { emitted = it })
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", emitted)
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    @Test
    fun topBar_showsActiveListName() {
        setScreen(activeList = MovieList(id = "1", name = "Action"))
        composeTestRule.onNodeWithText("Action").assertIsDisplayed()
    }

    @Test
    fun topBar_showsDefaultTitle_whenNoActiveList() {
        setScreen(activeList = null)
        composeTestRule.onNodeWithText("My Movie Tracker").assertIsDisplayed()
    }

    @Test
    fun topBar_menuButton_isDisplayed() {
        setScreen()
        composeTestRule.onNodeWithContentDescription("Open menu").assertIsDisplayed()
    }

    @Test
    fun topBar_statusSummary_showsWatchedAndWantCounts() {
        val movies = listOf(
            movie("A", status = WatchStatus.WATCHED),
            movie("B", status = WatchStatus.WANT_TO_WATCH),
            movie("C", status = WatchStatus.WANT_TO_WATCH)
        )
        setScreen(movies = movies)
        composeTestRule.onNodeWithText("1 watched · 2 want to watch").assertIsDisplayed()
    }

    @Test
    fun topBar_statusSummary_hiddenWhenNoMovies() {
        setScreen(movies = emptyList())
        // Summary only renders when watchedCount + wantCount > 0
        assertAbsent(hasText("watched ·", substring = true))
    }

    // ── Sort / filter dropdown ────────────────────────────────────────────────

    @Test
    fun sortButton_isDisplayed() {
        setScreen()
        composeTestRule.onNodeWithContentDescription("Sort / filter").assertIsDisplayed()
    }

    @Test
    fun sortDropdown_opens_showsAllSortOptions() {
        setScreen()
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (A–Z)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Title (Z–A)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Year (Oldest First)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Year (Newest First)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rating (Highest First)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rating (Lowest First)").assertIsDisplayed()
    }

    @Test
    fun sortDropdown_opens_showsAllFilterOptions() {
        setScreen()
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Show All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Watched Only").assertIsDisplayed()
        composeTestRule.onNodeWithText("Want to Watch Only").assertIsDisplayed()
    }

    @Test
    fun sortDropdown_selectSort_invokesOnSortOrderChange() {
        var selected: SortOrder? = null
        setScreen(onSortOrderChange = { selected = it })
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (Z–A)").performClick()
        assertEquals(SortOrder.TITLE_DESC, selected)
    }

    @Test
    fun sortDropdown_selectFilter_invokesOnWatchFilterChange() {
        var selected: WatchFilter? = null
        setScreen(onWatchFilterChange = { selected = it })
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Watched Only").performClick()
        assertEquals(WatchFilter.WATCHED, selected)
    }

    @Test
    fun sortDropdown_activeOption_hasSelectedCheckmark() {
        // One checkmark for active sort, one for active filter
        setScreen(sortOrder = SortOrder.YEAR_DESC, watchFilter = WatchFilter.ALL)
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onAllNodesWithContentDescription("Selected").onFirst().assertIsDisplayed()
    }

    @Test
    fun sortDropdown_closesAfterSelectingOption() {
        setScreen()
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (Z–A)").performClick()
        assertAbsent(hasText("Title (Z–A)"))
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    @Test
    fun snackbar_showsMessageText() {
        setScreen(snackbarMessage = "Something went wrong")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    // ── List selector ─────────────────────────────────────────────────────────

    @Test
    fun menuButton_click_revealsListSelectorDialog() {
        setScreen(lists = listOf(MovieList(id = "1", name = "All Movies")))
        composeTestRule.onNodeWithContentDescription("Open menu").performClick()
        composeTestRule.onNodeWithText("MY LISTS").assertIsDisplayed()
    }

    @Test
    fun listSelector_listItem_click_invokesOnListSelected() {
        val action = MovieList(id = "2", name = "Action")
        var selected: MovieList? = null
        setScreen(
            lists = listOf(MovieList(id = "1", name = "All Movies"), action),
            onListSelected = { selected = it }
        )
        composeTestRule.onNodeWithContentDescription("Open menu").performClick()
        composeTestRule.onNodeWithText("Action").performClick()
        assertEquals(action, selected)
    }

    @Test
    fun overflow_logOut_click_invokesOnLogOut() {
        var clicked = false
        setScreen(onLogOut = { clicked = true })
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Log Out").performClick()
        // Dropdown closes, confirmation dialog appears — click the Log Out button inside it
        composeTestRule.onNodeWithText("Log Out").performClick()
        assertTrue(clicked)
    }
}
