package com.apphud.sdk.internal.domain

import kotlinx.coroutines.CompletableDeferred

/**
 * Identity version 1 took the ids of version 2 over and no registration of it has succeeded yet
 * ([isPending]): the backend may not know its device id, so the cached customer is no registration
 * yet. Requests wait for this launch's first registration; once it ended, successfully or not, they
 * go as in SDK 3.6: with the cached customer, or after a registration when there is none.
 */
internal class Version1RegistrationGate(private val isPending: () -> Boolean) {

    @Volatile
    private var launchRegistration: CompletableDeferred<Unit>? = null

    /** At start, before anything can wait: this launch's first registration is to come. */
    fun closeIfPending(): CompletableDeferred<Unit>? {
        if (!isPending()) return null
        return CompletableDeferred<Unit>().also { launchRegistration = it }
    }

    /** Runs this launch's first registration: what waited for it goes when it ends, however it ends. */
    suspend fun <T> runLaunchRegistration(closed: CompletableDeferred<Unit>?, registration: suspend () -> T): T =
        try {
            registration()
        } finally {
            // Only this launch's: a later start may have closed the gate again meanwhile.
            closed?.complete(Unit)
        }

    /** The SDK was reset: nothing waits any more. */
    fun open() {
        launchRegistration?.complete(Unit)
    }

    /** Waits for this launch's first registration. */
    suspend fun awaitLaunchRegistration() {
        launchRegistration?.await()
    }

    /** A request's read of the cached customer: once this launch's first registration ended, then as SDK 3.6. */
    suspend fun <T> afterLaunchRegistration(read: () -> T): T {
        awaitLaunchRegistration()
        return read()
    }
}
