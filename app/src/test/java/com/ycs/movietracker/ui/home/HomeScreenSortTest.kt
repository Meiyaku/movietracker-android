package com.ycs.movietracker.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.SortOrder
import com.ycs.movietracker.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the HomeScreen sort dropdown — companion test story US-016-T.
 *
 * Run with: ./gradlew test
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class HomeScreenSortTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Sort control visibility ───────────────────────────────────────────────

    @Test
    fun sortIcon_isVisible() {
        composeTestRule.setContent {
            HomeScreen()
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").assertIsDisplayed()
    }

    // ── Dropdown expansion ───────────────────────────────────────────────────

    @Test
    fun tappingSortIcon_expandsDropdown() {
        composeTestRule.setContent {
            HomeScreen()
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (A\u2013Z)").assertIsDisplayed()
    }

    @Test
    fun sortDropdown_showsAllSixOptions() {
        composeTestRule.setContent {
            HomeScreen()
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (A\u2013Z)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Title (Z\u2013A)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Year (Oldest First)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Year (Newest First)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rating (Highest First)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rating (Lowest First)").assertIsDisplayed()
    }

    // ── Sort selection ────────────────────────────────────────────────────────

    @Test
    fun selectingSortOption_callsOnSortOrderChange_withCorrectOrder() {
        var selectedOrder: SortOrder? = null
        composeTestRule.setContent {
            HomeScreen(onSortOrderChange = { selectedOrder = it })
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (Z\u2013A)").performClick()
        assertEquals(SortOrder.TITLE_DESC, selectedOrder)
    }

    @Test
    fun selectingYearOldestFirst_callsOnSortOrderChange_withYearAsc() {
        var selectedOrder: SortOrder? = null
        composeTestRule.setContent {
            HomeScreen(onSortOrderChange = { selectedOrder = it })
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Year (Oldest First)").performClick()
        assertEquals(SortOrder.YEAR_ASC, selectedOrder)
    }

    @Test
    fun selectingRatingHighestFirst_callsOnSortOrderChange_withRatingDesc() {
        var selectedOrder: SortOrder? = null
        composeTestRule.setContent {
            HomeScreen(onSortOrderChange = { selectedOrder = it })
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Rating (Highest First)").performClick()
        assertEquals(SortOrder.RATING_DESC, selectedOrder)
    }

    @Test
    fun selectingSortOption_closesDropdown() {
        composeTestRule.setContent {
            HomeScreen()
        }
        composeTestRule.onNodeWithContentDescription("Sort / filter").performClick()
        composeTestRule.onNodeWithText("Title (Z\u2013A)").performClick()
        // After selection the dropdown should be gone
        composeTestRule.onNodeWithText("Year (Oldest First)").assertDoesNotExist()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Suppress("unused")
    private fun sampleMovie(id: String = "m1", title: String = "Inception") = Movie(
        id = id,
        title = title,
        status = WatchStatus.WATCHED,
        createdAt = Timestamp.now()
    )
}
