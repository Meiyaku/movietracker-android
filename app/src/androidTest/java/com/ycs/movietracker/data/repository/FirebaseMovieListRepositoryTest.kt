package com.ycs.movietracker.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.MovieList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for [FirebaseMovieListRepository] running against the local Firebase emulator.
 *
 * Prerequisites:
 *   1. Install Firebase CLI: `npm install -g firebase-tools`
 *   2. From the project root (movietracker/): `firebase emulators:start --only firestore,auth`
 *   3. Run: `./gradlew connectedAndroidTest` (requires a running Android Emulator)
 *
 * Each test uses a unique UID so data is fully isolated — no teardown required.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseMovieListRepositoryTest {

    private lateinit var repo: FirebaseMovieListRepository

    /** Fresh UID per test — all Firestore paths are unique, no cross-test interference. */
    private lateinit var uid: String

    @Before
    fun setUp() {
        EmulatorSetup.configure()
        uid = "test-list-${UUID.randomUUID()}"
        val fakeRemoteConfig = object : RemoteConfigRepository {
            override val pageSize = 50
            override val maxRetryAttempts = 3
            override val isTmdbSearchEnabled = MutableStateFlow(true)
            override val tmdbApiKey = MutableStateFlow("")
            override val whatsNew = MutableStateFlow("")
            override val whatsNewVersion = MutableStateFlow(0)
        }
        repo = FirebaseMovieListRepository(EmulatorSetup.firestore(), fakeRemoteConfig)
    }

    // ── createList ────────────────────────────────────────────────────────────

    @Test
    fun createList_returnsNonBlankId() = runBlocking {
        val result = repo.createList(uid, MovieList(name = "All Movies"))

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotBlank())
    }

    @Test
    fun createList_listIncludedInGetLists() = runBlocking {
        repo.createList(uid, MovieList(name = "Watchlist"))

        val lists = withTimeout(10_000) {
            repo.getLists(uid).first().getOrThrow()
        }

        assertEquals(1, lists.size)
        assertEquals("Watchlist", lists.first().name)
    }

    @Test
    fun createList_multipleListsAllReturnedByGetLists() = runBlocking {
        repo.createList(uid, MovieList(name = "Favourites"))
        repo.createList(uid, MovieList(name = "Watchlist"))

        val lists = withTimeout(10_000) {
            repo.getLists(uid).first().getOrThrow()
        }

        assertEquals(2, lists.size)
        val names = lists.map { it.name }.toSet()
        assertTrue("Favourites" in names)
        assertTrue("Watchlist" in names)
    }

    // ── updateList ────────────────────────────────────────────────────────────

    @Test
    fun updateList_nameReflectedInGetLists() = runBlocking {
        val id = repo.createList(uid, MovieList(name = "Old Name")).getOrThrow()

        repo.updateList(uid, MovieList(id = id, name = "New Name"))

        val lists = withTimeout(10_000) {
            repo.getLists(uid).first().getOrThrow()
        }
        assertEquals("New Name", lists.first().name)
    }

    // ── deleteList ────────────────────────────────────────────────────────────

    @Test
    fun deleteList_listExcludedFromGetLists() = runBlocking {
        val idToDelete = repo.createList(uid, MovieList(name = "To Delete")).getOrThrow()
        repo.createList(uid, MovieList(name = "Keep"))

        repo.deleteList(uid, idToDelete)

        val lists = withTimeout(10_000) {
            repo.getLists(uid).first().getOrThrow()
        }
        assertEquals(1, lists.size)
        assertEquals("Keep", lists.first().name)
        assertFalse(lists.any { it.id == idToDelete })
    }

    @Test
    fun deleteList_getListsEmptyAfterDeletingOnlyList() = runBlocking {
        val id = repo.createList(uid, MovieList(name = "Solo")).getOrThrow()

        repo.deleteList(uid, id)

        val lists = withTimeout(10_000) {
            repo.getLists(uid).first().getOrThrow()
        }
        assertTrue(lists.isEmpty())
    }

    // ── getLists (real-time) ──────────────────────────────────────────────────

    @Test
    fun getLists_emitsEmptyListWhenNoListsExist() = runBlocking {
        val lists = withTimeout(10_000) {
            repo.getLists(uid).first().getOrThrow()
        }
        assertTrue(lists.isEmpty())
    }
}
