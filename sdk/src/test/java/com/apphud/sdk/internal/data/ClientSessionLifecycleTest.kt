package com.apphud.sdk.internal.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.apphud.sdk.storage.ClientSessionStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientSessionLifecycleTest {

    private class FakeStorage : ClientSessionStorage {
        override var clientSessionId: String? = null
        override var clientSessionNumber: Int = 0
        override var clientSessionLastBackgroundAt: Long = 0L
    }

    private class ProcessOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var nowMs = 1_800_000_000_000L
    private val repository = ClientSessionRepository(FakeStorage(), now = { nowMs })
    private val process = ProcessOwner()

    private fun attach(startedForUser: Boolean) =
        ClientSessionLifecycle.attach(repository, process.registry, startedForUser = { startedForUser })

    @Test
    fun `GIVEN process started for the user EXPECT session opened at attach`() {
        attach(startedForUser = true)

        assertEquals(1, repository.sessionNumber())
    }

    @Test
    fun `GIVEN background-only process start EXPECT no session at attach`() {
        attach(startedForUser = false)

        assertEquals(0, repository.sessionNumber())
    }

    @Test
    fun `GIVEN process start looked like an open WHEN first start comes after 30 minutes EXPECT new session`() {
        attach(startedForUser = true)

        nowMs += ClientSessionRepository.BACKGROUND_TIMEOUT_MS + 1
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(2, repository.sessionNumber())
    }

    @Test
    fun `GIVEN background-only process start WHEN process lifecycle starts EXPECT session opened`() {
        attach(startedForUser = false)

        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(1, repository.sessionNumber())
    }

    @Test
    fun `GIVEN opened app WHEN process stops and starts after 30 minutes EXPECT new session`() {
        attach(startedForUser = true)
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        nowMs += ClientSessionRepository.BACKGROUND_TIMEOUT_MS + 1
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(2, repository.sessionNumber())
    }

    @Test
    fun `GIVEN opened app WHEN process stops and starts after a minute EXPECT same session`() {
        attach(startedForUser = true)
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        nowMs += 60_000L
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(1, repository.sessionNumber())
    }
}
