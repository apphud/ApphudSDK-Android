package com.apphud.sdk.internal.data

import com.apphud.sdk.storage.ClientSessionStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSessionRepositoryTest {

    private class FakeStorage : ClientSessionStorage {
        override var clientSessionId: String? = null
        override var clientSessionNumber: Int = 0
        override var clientSessionLastBackgroundAt: Long = 0L
    }

    private val storage = FakeStorage()
    private var nowMs = 1_800_000_000_000L
    private var idCounter = 0
    private val overTimeout = ClientSessionRepository.BACKGROUND_TIMEOUT_MS + 1

    private fun repository() = ClientSessionRepository(storage, now = { nowMs }, newId = { "id-${++idCounter}" })

    private fun ClientSessionRepository.background(ms: Long) {
        onBackground()
        nowMs += ms
        onForeground()
    }

    private fun openedRepository() = repository().apply { onAppOpened() }

    // Launch and background-only starts

    @Test
    fun `GIVEN default id generator EXPECT session id is a lowercase UUID`() {
        val repository = ClientSessionRepository(storage).apply { onAppOpened() }

        assertTrue(repository.sessionId().matches(UUID_PATTERN))
    }

    @Test
    fun `GIVEN fresh install and background-only start EXPECT number stays 0`() {
        val repository = repository()
        repository.sessionId()

        assertEquals(0, repository.sessionNumber())
    }

    @Test
    fun `GIVEN fresh install and background-only start EXPECT id persisted`() {
        val id = repository().sessionId()

        assertEquals(id, storage.clientSessionId)
    }

    @Test
    fun `GIVEN fresh install WHEN app opened EXPECT number 1`() {
        assertEquals(1, openedRepository().sessionNumber())
    }

    @Test
    fun `GIVEN id before first session WHEN app opened EXPECT new id`() {
        val repository = repository()
        val before = repository.sessionId()

        repository.onAppOpened()

        assertNotEquals(before, repository.sessionId())
    }

    @Test
    fun `GIVEN previous session and background-only start EXPECT previous id`() {
        storage.clientSessionId = "previous"
        storage.clientSessionNumber = 5

        assertEquals("previous", repository().sessionId())
    }

    @Test
    fun `GIVEN previous session and background-only start EXPECT number unchanged`() {
        storage.clientSessionId = "previous"
        storage.clientSessionNumber = 5
        val repository = repository()
        repository.sessionId()

        assertEquals(5, repository.sessionNumber())
    }

    @Test
    fun `GIVEN background-only start WHEN first foreground EXPECT new session`() {
        storage.clientSessionId = "previous"
        storage.clientSessionNumber = 5
        val repository = repository()

        repository.onForeground()

        assertEquals(6, repository.sessionNumber())
    }

    @Test
    fun `GIVEN app opened at start WHEN first foreground EXPECT same session`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.onForeground()

        assertEquals(id, repository.sessionId())
    }

    // Background

    @Test
    fun `GIVEN background over 30 minutes EXPECT new id`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.background(overTimeout)

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN background over 30 minutes EXPECT number incremented`() {
        val repository = openedRepository()

        repository.background(overTimeout)

        assertEquals(2, repository.sessionNumber())
    }

    @Test
    fun `GIVEN background of exactly 30 minutes EXPECT same id`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.background(ClientSessionRepository.BACKGROUND_TIMEOUT_MS)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN short background EXPECT same id`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.background(60_000L)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN clock moved back EXPECT same id`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.background(-3_600_000L)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN repeated stop events EXPECT background counted from the first`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.onBackground()
        nowMs += 1_000_000L
        repository.onBackground()
        nowMs += 900_000L
        repository.onForeground()

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN foreground without background EXPECT same id`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        nowMs += 2 * 3_600_000L
        repository.onForeground()

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN background EXPECT background time persisted`() {
        val repository = openedRepository()

        repository.onBackground()

        assertEquals(nowMs, storage.clientSessionLastBackgroundAt)
    }

    // Logout and persistence

    @Test
    fun `GIVEN logout EXPECT new id`() {
        val repository = openedRepository()
        val id = repository.sessionId()

        repository.onLogout()

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN logout EXPECT number incremented`() {
        val repository = openedRepository()

        repository.onLogout()

        assertEquals(2, repository.sessionNumber())
    }

    @Test
    fun `GIVEN new background-only process after logout EXPECT id from logout kept`() {
        val first = openedRepository()
        first.onLogout()
        val id = first.sessionId()

        assertEquals(id, repository().sessionId())
    }

    @Test
    fun `GIVEN new process opened EXPECT number continues`() {
        openedRepository()

        assertEquals(2, openedRepository().sessionNumber())
    }

    // External mode

    @Test
    fun `GIVEN external id EXPECT sent exactly as given`() {
        val repository = openedRepository()

        repository.setExternalSessionId("Host Session-1")

        assertEquals("Host Session-1", repository.sessionId())
    }

    @Test
    fun `GIVEN external mode and background over 30 minutes EXPECT same id`() {
        val repository = openedRepository()
        repository.setExternalSessionId("host")

        repository.background(overTimeout)

        assertEquals("host", repository.sessionId())
    }

    @Test
    fun `GIVEN external mode and background over 30 minutes EXPECT number unchanged`() {
        val repository = openedRepository()
        repository.setExternalSessionId("host")

        repository.background(overTimeout)

        assertEquals(1, repository.sessionNumber())
    }

    @Test
    fun `GIVEN external mode and logout EXPECT same id`() {
        val repository = openedRepository()
        repository.setExternalSessionId("host")

        repository.onLogout()

        assertEquals("host", repository.sessionId())
    }

    @Test
    fun `GIVEN external mode and logout EXPECT number unchanged`() {
        val repository = openedRepository()
        repository.setExternalSessionId("host")

        repository.onLogout()

        assertEquals(1, repository.sessionNumber())
    }

    @Test
    fun `GIVEN external id set twice EXPECT latest id`() {
        val repository = openedRepository()
        repository.setExternalSessionId("host-1")

        repository.setExternalSessionId("host-2")

        assertEquals("host-2", repository.sessionId())
    }

    @Test
    fun `GIVEN external id before app opened EXPECT number unchanged on open`() {
        val repository = repository()
        repository.setExternalSessionId("host")

        repository.onAppOpened()

        assertEquals(0, repository.sessionNumber())
    }

    @Test
    fun `GIVEN external id before first foreground EXPECT host id kept`() {
        val repository = repository()
        repository.setExternalSessionId("host")

        repository.onForeground()

        assertEquals("host", repository.sessionId())
    }

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
