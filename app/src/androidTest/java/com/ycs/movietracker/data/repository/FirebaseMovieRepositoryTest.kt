package com.ycs.movietracker.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.NewMovie
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Integration tests for [FirebaseMovieRepository] running against the local Firebase emulator.
 *
 * Prerequisites:
 *   1. Install Firebase CLI: `npm install -g firebase-tools`
 *   2. From the project root (movietracker/): `firebase emulators:start --only firestore,auth`
 *   3. Run: `./gradlew connectedAndroidTest` (requires a running Android Emulator)
 *
 * Each test uses a unique UID so data is fully isolated — no teardown required.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseMovieRepositoryTest {

    private lateinit var repo: FirebaseMovieRepository

    /** Fresh UID per test — all Firestore paths are unique, no cross-test interference. */
    private lateinit var uid: String

    private val fakeRemoteConfig = object : RemoteConfigRepository {
        override val pageSize = 50
        override val maxRetryAttempts = 3
        override val isTmdbSearchEnabled = MutableStateFlow(true)
        override val tmdbApiKey = MutableStateFlow("")
    }

    @Before
    fun setUp() {
        EmulatorSetup.configure()
        uid = "test-movie-${UUID.randomUUID()}"
        repo = FirebaseMovieRepository(EmulatorSetup.firestore(), fakeRemoteConfig)
    }

    // ── addMovie ──────────────────────────────────────────────────────────────

    @Test
    fun addMovie_returnsNonBlankId() = runBlocking {
        val result = repo.addMovie(uid, newMovie())
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().id.isNotBlank())
    }

    @Test
    fun addMovie_movieIsRetrievableAfterAdd() = runBlocking {
        val listId = "list-a"
        repo.addMovie(uid, newMovie(title = "Dune", listIds = listOf(listId)))

        val page = repo.getMoviesPage(uid, listId, 10, null).getOrThrow()

        assertEquals(1, page.movies.size)
        assertEquals("Dune", page.movies.first().title)
    }

    // ── updateMovie ───────────────────────────────────────────────────────────

    @Test
    fun updateMovie_fieldsReflectedInSubsequentQuery() = runBlocking {
        val listId = "list-a"
        val id = repo.addMovie(uid, newMovie(title = "Original", listIds = listOf(listId))).getOrThrow().id

        repo.updateMovie(uid, movie(id = id, title = "Updated", listIds = listOf(listId)))

        val page = repo.getMoviesPage(uid, listId, 10, null).getOrThrow()
        assertEquals("Updated", page.movies.first().title)
    }

    // ── deleteMovie ───────────────────────────────────────────────────────────

    @Test
    fun deleteMovie_notReturnedAfterDelete() = runBlocking {
        val listId = "list-a"
        val id = repo.addMovie(uid, newMovie(listIds = listOf(listId))).getOrThrow().id

        repo.deleteMovie(uid, id)

        val page = repo.getMoviesPage(uid, listId, 10, null).getOrThrow()
        assertTrue(page.movies.isEmpty())
    }

    // ── getMoviesPage ─────────────────────────────────────────────────────────

    @Test
    fun getMoviesPage_emptyResultWhenNoMovies() = runBlocking {
        val page = repo.getMoviesPage(uid, "empty-list", 10, null).getOrThrow()

        assertTrue(page.movies.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun getMoviesPage_returnsOnlyMoviesForSpecifiedList() = runBlocking {
        repo.addMovie(uid, newMovie(title = "In A", listIds = listOf("list-a")))
        repo.addMovie(uid, newMovie(title = "In B", listIds = listOf("list-b")))

        val page = repo.getMoviesPage(uid, "list-a", 10, null).getOrThrow()

        assertEquals(1, page.movies.size)
        assertEquals("In A", page.movies.first().title)
    }

    @Test
    fun getMoviesPage_hasMoreTrueWhenPageSizeFull() = runBlocking {
        val listId = "list-a"
        repeat(3) { repo.addMovie(uid, newMovie(listIds = listOf(listId))) }

        val page = repo.getMoviesPage(uid, listId, pageSize = 3, afterId = null).getOrThrow()

        assertEquals(3, page.movies.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun getMoviesPage_hasMoreFalseWhenBelowPageSize() = runBlocking {
        val listId = "list-a"
        repeat(2) { repo.addMovie(uid, newMovie(listIds = listOf(listId))) }

        val page = repo.getMoviesPage(uid, listId, pageSize = 3, afterId = null).getOrThrow()

        assertEquals(2, page.movies.size)
        assertFalse(page.hasMore)
    }

    @Test
    fun getMoviesPage_paginationCursorExcludesPreviousPage() = runBlocking {
        val listId = "list-a"
        repeat(5) { repo.addMovie(uid, newMovie(listIds = listOf(listId))) }

        val page1 = repo.getMoviesPage(uid, listId, pageSize = 3, afterId = null).getOrThrow()
        assertEquals(3, page1.movies.size)
        assertTrue(page1.hasMore)

        val page2 = repo.getMoviesPage(uid, listId, pageSize = 3, afterId = page1.lastId).getOrThrow()
        assertEquals(2, page2.movies.size)
        assertFalse(page2.hasMore)

        // No overlap between pages
        val allIds = (page1.movies + page2.movies).map { it.id }.toSet()
        assertEquals(5, allIds.size)
    }

    // ── getMovieById ──────────────────────────────────────────────────────────

    @Test
    fun getMovieById_returnsMovie_whenDocumentExists() = runBlocking {
        val added = repo.addMovie(uid, newMovie(title = "Dune")).getOrThrow()

        val result = repo.getMovieById(uid, added.id)

        assertTrue(result.isSuccess)
        assertEquals(added.id, result.getOrThrow().id)
        assertEquals("Dune", result.getOrThrow().title)
    }

    @Test
    fun getMovieById_returnsFailure_whenDocumentDoesNotExist() = runBlocking {
        val result = repo.getMovieById(uid, "nonexistent-id")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    // ── checkDuplicate ────────────────────────────────────────────────────────

    @Test
    fun checkDuplicate_returnsTrue_whenExactMatchExists() = runBlocking {
        repo.addMovie(uid, NewMovie(title = "Inception", year = 2010, genre = "Sci-Fi",
            listIds = listOf("list-1"), createdAt = com.google.firebase.Timestamp.now()))

        val result = repo.checkDuplicate(uid, "Inception", 2010, "Sci-Fi", excludeId = null)

        assertTrue(result.getOrThrow())
    }

    @Test
    fun checkDuplicate_isCaseInsensitive() = runBlocking {
        repo.addMovie(uid, NewMovie(title = "inception", year = 2010, genre = "sci-fi",
            listIds = listOf("list-1"), createdAt = com.google.firebase.Timestamp.now()))

        val result = repo.checkDuplicate(uid, "INCEPTION", 2010, "SCI-FI", excludeId = null)

        assertTrue(result.getOrThrow())
    }

    @Test
    fun checkDuplicate_returnsFalse_whenTitleDiffers() = runBlocking {
        repo.addMovie(uid, NewMovie(title = "Inception", year = 2010, genre = "Sci-Fi",
            listIds = listOf("list-1"), createdAt = com.google.firebase.Timestamp.now()))

        val result = repo.checkDuplicate(uid, "Interstellar", 2010, "Sci-Fi", excludeId = null)

        assertFalse(result.getOrThrow())
    }

    @Test
    fun checkDuplicate_returnsFalse_whenExcludeIdMatchesOnlyDocument() = runBlocking {
        val added = repo.addMovie(uid, NewMovie(title = "Inception", year = 2010, genre = "Sci-Fi",
            listIds = listOf("list-1"), createdAt = com.google.firebase.Timestamp.now())).getOrThrow()

        // Same dedupeKey but excludeId matches the only document — not a duplicate
        val result = repo.checkDuplicate(uid, "Inception", 2010, "Sci-Fi", excludeId = added.id)

        assertFalse(result.getOrThrow())
    }

    @Test
    fun checkDuplicate_matchesNullYearAndGenre() = runBlocking {
        repo.addMovie(uid, NewMovie(title = "Inception", year = null, genre = null,
            listIds = listOf("list-1"), createdAt = com.google.firebase.Timestamp.now()))

        val result = repo.checkDuplicate(uid, "Inception", null, null, excludeId = null)

        assertTrue(result.getOrThrow())
    }

    // ── removeListFromMovies ──────────────────────────────────────────────────

    @Test
    fun removeListFromMovies_moviesNoLongerReturnedForRemovedList() = runBlocking {
        val listId = "list-remove"
        repeat(3) { repo.addMovie(uid, newMovie(listIds = listOf(listId))) }

        repo.removeListFromMovies(uid, listId)

        val page = repo.getMoviesPage(uid, listId, 10, null).getOrThrow()
        assertTrue(page.movies.isEmpty())
    }

    @Test
    fun removeListFromMovies_preservesOtherListIdsInMultiListMovies() = runBlocking {
        val listA = "list-a"
        val listB = "list-b"
        // Movie belongs to both lists
        repo.addMovie(uid, newMovie(listIds = listOf(listA, listB)))

        repo.removeListFromMovies(uid, listA)

        // list-a should now be empty
        val pageA = repo.getMoviesPage(uid, listA, 10, null).getOrThrow()
        assertTrue(pageA.movies.isEmpty())

        // list-b should still have the movie
        val pageB = repo.getMoviesPage(uid, listB, 10, null).getOrThrow()
        assertEquals(1, pageB.movies.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun newMovie(
        title: String = "Test Movie",
        listIds: List<String> = listOf("list-1"),
        status: WatchStatus = WatchStatus.WANT_TO_WATCH,
        rating: Int? = null
    ) = NewMovie(
        title = title,
        status = status,
        rating = rating,
        listIds = listIds,
        createdAt = Timestamp.now()
    )

    private fun movie(
        id: String,
        title: String = "Test Movie",
        listIds: List<String> = listOf("list-1"),
        status: WatchStatus = WatchStatus.WANT_TO_WATCH,
        rating: Int? = null
    ) = Movie(
        id = id,
        title = title,
        status = status,
        rating = rating,
        listIds = listIds,
        createdAt = Timestamp.now()
    )
}
