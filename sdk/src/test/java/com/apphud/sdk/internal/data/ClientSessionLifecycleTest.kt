package com.apphud.sdk.internal.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.apphud.sdk.storage.ClientSessionStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClientSessionLifecycleTest {

    private class FakeStorage : ClientSessionStorage {
        override var clientSessionId: String? = null
        override var clientSessionLastBackgroundAt: Long = 0L
    }

    private class ProcessOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var nowMs = 1_800_000_000_000L
    private val storage = FakeStorage()
    private val repository = ClientSessionRepository(storage, now = { nowMs })
    private val process = ProcessOwner()

    private fun attach() = ClientSessionLifecycle.attach(repository, process.registry)

    private fun savedSession(backgroundMinutesAgo: Long) {
        storage.clientSessionId = "previous"
        storage.clientSessionLastBackgroundAt = nowMs - backgroundMinutesAgo * 60_000L
    }

    @Test
    fun `GIVEN process start within 30 minutes of the last background EXPECT saved session`() {
        savedSession(backgroundMinutesAgo = 10)

        attach()

        assertEquals("previous", repository.sessionId())
    }

    @Test
    fun `GIVEN process start over 30 minutes after the last background EXPECT new session`() {
        savedSession(backgroundMinutesAgo = 31)

        attach()

        assertNotEquals("previous", repository.sessionId())
    }

    @Test
    fun `GIVEN process start WHEN the app opens soon EXPECT one id`() {
        savedSession(backgroundMinutesAgo = 10)
        attach()
        val id = repository.sessionId()

        nowMs += 60_000L
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN process start WHEN first start comes over 30 minutes later EXPECT new session`() {
        attach()
        val id = repository.sessionId()

        nowMs += ClientSessionRepository.BACKGROUND_TIMEOUT_MS + 1
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN opened app WHEN process stops and starts after 30 minutes EXPECT new session`() {
        attach()
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        val id = repository.sessionId()

        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        nowMs += ClientSessionRepository.BACKGROUND_TIMEOUT_MS + 1
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN opened app WHEN process stops and starts after a minute EXPECT same session`() {
        attach()
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        val id = repository.sessionId()

        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        nowMs += 60_000L
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN process stop EXPECT background time saved`() {
        attach()
        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        nowMs += 60_000L

        process.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        assertEquals(nowMs, storage.clientSessionLastBackgroundAt)
    }
}
