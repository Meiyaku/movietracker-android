package com.ycs.movietracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Guards `useEmulator()` so it is called exactly once across all repository test classes.
 *
 * Firebase SDK throws `IllegalStateException` if `useEmulator()` is called after Firestore
 * has already been used, or if it is called more than once on the same instance. The
 * `AtomicBoolean` here ensures correctness regardless of which test class initialises first.
 *
 * Call [configure] in every repository test class's `@Before` method.
 *
 * Host note: `10.0.2.2` is the Android Emulator's alias for the host machine's `localhost`.
 * When testing on a physical device, replace this with the host machine's LAN IP address.
 *
 * Emulator ports:
 *   - Firestore: 8080
 *   - Auth:      9099
 *
 * Start both with: `firebase emulators:start --only firestore,auth`
 */
internal object EmulatorSetup {
    private val configured = AtomicBoolean(false)

    private const val EMULATOR_HOST = "10.0.2.2"
    private const val FIRESTORE_PORT = 8080
    private const val AUTH_PORT = 9099

    fun configure() {
        if (configured.compareAndSet(false, true)) {
            FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, AUTH_PORT)
        }
    }

    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
}
