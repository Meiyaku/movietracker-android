package com.ycs.movietracker.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the HomeScreen movie list — companion test story US-015-T.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
// Pin a realistic phone screen size: the adaptive movie grid needs a true device width to
// resolve to 2 columns — Robolectric's default display is too narrow and collapses it to 1.
@Config(sdk = [33], qualifiers = "+w411dp-h891dp")
class HomeScreenMovieListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Empty states ──────────────────────────────────────────────────────────

    @Test
    fun emptyMovieList_withNoSearch_showsAddMoviesMessage() {
        composeTestRule.setContent {
            HomeScreen(movies = emptyList(), searchQuery = "")
        }
        composeTestRule.onNodeWithText("No movies yet. Add one!").assertIsDisplayed()
    }

    @Test
    fun emptyMovieList_withSearchQuery_showsNoMatchMessage() {
        composeTestRule.setContent {
            HomeScreen(movies = emptyList(), searchQuery = "Dune")
        }
        composeTestRule.onNodeWithText("No movies match your search.").assertIsDisplayed()
    }

    @Test
    fun emptyMovieList_withNoSearch_doesNotShowSearchEmptyMessage() {
        composeTestRule.setContent {
            HomeScreen(movies = emptyList(), searchQuery = "")
        }
        composeTestRule.onNodeWithText("No movies match your search.").assertIsNotDisplayed()
    }

    @Test
    fun emptyMovieList_withSearchQuery_doesNotShowAddMoviesMessage() {
        composeTestRule.setContent {
            HomeScreen(movies = emptyList(), searchQuery = "Dune")
        }
        composeTestRule.onNodeWithText("No movies yet. Add one!").assertIsNotDisplayed()
    }

    // ── Movie list rendering ──────────────────────────────────────────────────

    @Test
    fun movieList_showsMovieTitle() {
        composeTestRule.setContent {
            HomeScreen(movies = listOf(watchedMovie(title = "Inception")))
        }
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
    }

    @Test
    fun movieList_showsMultipleTitles() {
        composeTestRule.setContent {
            HomeScreen(
                movies = listOf(
                    watchedMovie(id = "1", title = "Inception"),
                    wantToWatchMovie(id = "2", title = "Dune")
                )
            )
        }
        composeTestRule.onNodeWithText("Inception").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dune").assertIsDisplayed()
    }

    @Test
    fun watchedMovie_showsWatchedBadge() {
        composeTestRule.setContent {
            HomeScreen(movies = listOf(watchedMovie(title = "Inception")))
        }
        composeTestRule.onNodeWithText("Watched").assertIsDisplayed()
    }

    @Test
    fun wantToWatchMovie_showsWantToWatchBadge() {
        composeTestRule.setContent {
            HomeScreen(movies = listOf(wantToWatchMovie(title = "Dune")))
        }
        composeTestRule.onNodeWithText("Want to Watch").assertIsDisplayed()
    }

    @Test
    fun movieList_doesNotShowEmptyState_whenMoviesPresent() {
        composeTestRule.setContent {
            HomeScreen(movies = listOf(watchedMovie(title = "Inception")))
        }
        composeTestRule.onNodeWithText("No movies yet. Add one!").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("No movies match your search.").assertIsNotDisplayed()
    }

    // ── Tap to navigate ───────────────────────────────────────────────────────

    @Test
    fun tappingMovieRow_invokesOnMovieClickWithCorrectMovie() {
        val movie = watchedMovie(id = "m1", title = "Inception")
        var clickedMovie: Movie? = null
        composeTestRule.setContent {
            HomeScreen(
                movies = listOf(movie),
                onMovieClick = { clickedMovie = it }
            )
        }
        composeTestRule.onNodeWithText("Inception").performClick()
        assertEquals(movie, clickedMovie)
    }

    @Test
    fun tappingFirstMovieRow_doesNotInvokeClickForOtherMovies() {
        val movie1 = watchedMovie(id = "m1", title = "Inception")
        val movie2 = wantToWatchMovie(id = "m2", title = "Dune")
        val clickedIds = mutableListOf<String>()
        composeTestRule.setContent {
            HomeScreen(
                movies = listOf(movie1, movie2),
                onMovieClick = { clickedIds.add(it.id) }
            )
        }
        composeTestRule.onNodeWithText("Inception").performClick()
        assertEquals(listOf("m1"), clickedIds)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun watchedMovie(
        id: String = "m1",
        title: String = "Sample",
        rating: Double? = 4.0
    ) = Movie(
        id = id,
        title = title,
        status = WatchStatus.WATCHED,
        rating = rating,
        createdAt = Timestamp.now()
    )

    private fun wantToWatchMovie(
        id: String = "m2",
        title: String = "Sample 2"
    ) = Movie(
        id = id,
        title = title,
        status = WatchStatus.WANT_TO_WATCH,
        createdAt = Timestamp.now()
    )
}
