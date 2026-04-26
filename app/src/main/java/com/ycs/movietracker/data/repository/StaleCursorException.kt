package com.ycs.movietracker.data.repository

/**
 * Thrown when the Firestore document used as a pagination cursor no longer exists.
 * This happens when a movie is deleted while the user is mid-pagination.
 * Callers should reset the cursor and restart pagination from page 1.
 */
class StaleCursorException(documentId: String) :
    Exception("Pagination cursor document '$documentId' no longer exists")
