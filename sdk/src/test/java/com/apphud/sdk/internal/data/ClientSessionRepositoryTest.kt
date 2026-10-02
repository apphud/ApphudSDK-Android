package com.apphud.sdk.internal.data

import com.apphud.sdk.storage.ClientSessionStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSessionRepositoryTest {

    private class FakeStorage : ClientSessionStorage {
        override var clientSessionId: String? = null
        override var clientSessionLastBackgroundAt: Long = 0L
    }

    private val storage = FakeStorage()
    private var nowMs = 1_800_000_000_000L
    private var idCounter = 0
    private val overTimeout = ClientSessionRepository.BACKGROUND_TIMEOUT_MS + 1

    private fun repository() = ClientSessionRepository(storage, now = { nowMs }, newId = { "id-${++idCounter}" })

    /** A new process over the same storage. An opened one gets ON_START right away. */
    private fun started(opened: Boolean = true) = repository().apply {
        onProcessStart()
        if (opened) onForeground()
    }

    private fun ClientSessionRepository.background(ms: Long) {
        onBackground()
        nowMs += ms
        onForeground()
    }

    /** The user used the app and sent it to the background; the process then ended. */
    private fun sessionSentToBackground(): String {
        val repository = started()
        repository.onBackground()
        return repository.sessionId()
    }

    // Process start

    @Test
    fun `GIVEN default id generator EXPECT session id is a lowercase UUID`() {
        val repository = ClientSessionRepository(storage).apply { onProcessStart() }

        assertTrue(repository.sessionId().matches(UUID_PATTERN))
    }

    @Test
    fun `GIVEN fresh install EXPECT new session saved with the start as background time`() {
        val repository = started(opened = false)

        assertEquals(repository.sessionId(), storage.clientSessionId)
        assertEquals(nowMs, storage.clientSessionLastBackgroundAt)
    }

    @Test
    fun `GIVEN background start within 30 minutes of the background EXPECT saved session continued`() {
        val id = sessionSentToBackground()
        nowMs += 10 * 60_000L

        assertEquals(id, started(opened = false).sessionId())
    }

    @Test
    fun `GIVEN user start within 30 minutes of the background EXPECT new session`() {
        val id = sessionSentToBackground()
        nowMs += 10 * 60_000L

        assertNotEquals(id, started().sessionId())
    }

    @Test
    fun `GIVEN cold start EXPECT requests before the first ON_START carry the start session`() {
        val id = sessionSentToBackground()
        nowMs += 10 * 60_000L
        val repository = started(opened = false)
        assertEquals(id, repository.sessionId())

        repository.onForeground()

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN first foreground WHEN foreground again without background EXPECT session kept`() {
        val repository = started(opened = false)
        repository.onForeground()
        val id = repository.sessionId()

        nowMs += 2 * 3_600_000L
        repository.onForeground()

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN start at exactly 30 minutes EXPECT saved session continued`() {
        val id = sessionSentToBackground()
        nowMs += ClientSessionRepository.BACKGROUND_TIMEOUT_MS

        assertEquals(id, started(opened = false).sessionId())
    }

    @Test
    fun `GIVEN start after 30 minutes EXPECT new session counted from the start`() {
        val id = sessionSentToBackground()
        nowMs += overTimeout

        val repository = started(opened = false)

        assertNotEquals(id, repository.sessionId())
        assertEquals(repository.sessionId(), storage.clientSessionId)
        assertEquals(nowMs, storage.clientSessionLastBackgroundAt)
    }

    @Test
    fun `GIVEN saved id without background time EXPECT new session`() {
        storage.clientSessionId = "previous"

        assertNotEquals("previous", started().sessionId())
    }

    @Test
    fun `GIVEN saved background time in the future EXPECT new session`() {
        val id = sessionSentToBackground()
        nowMs -= 60_000L

        assertNotEquals(id, started(opened = false).sessionId())
    }

    @Test
    fun `GIVEN repeated starts EXPECT session not extended`() {
        val id = sessionSentToBackground()
        nowMs += 20 * 60_000L
        assertEquals(id, started(opened = false).sessionId())

        nowMs += 15 * 60_000L

        assertNotEquals(id, started(opened = false).sessionId())
    }

    @Test
    fun `GIVEN session started with the process EXPECT next start within 30 minutes continues it`() {
        sessionSentToBackground()
        nowMs += 2 * 3_600_000L
        val id = started(opened = false).sessionId()

        nowMs += 10 * 60_000L

        assertEquals(id, started(opened = false).sessionId())
    }

    @Test
    fun `GIVEN background start WHEN the user opens the app soon EXPECT new session`() {
        val id = sessionSentToBackground()
        nowMs += 10 * 60_000L
        val repository = started(opened = false)

        nowMs += 5 * 60_000L
        repository.onForeground()

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN opened app EXPECT not counted as in the background`() {
        val repository = started()
        val id = repository.sessionId()

        nowMs += 2 * 3_600_000L
        repository.onForeground()

        assertEquals(id, repository.sessionId())
    }

    // Without the lifecycle (attach failed): the first read applies the start rule

    @Test
    fun `GIVEN no lifecycle and fresh install EXPECT new session saved on first read`() {
        val id = repository().sessionId()

        assertEquals(id, storage.clientSessionId)
        assertEquals(nowMs, storage.clientSessionLastBackgroundAt)
    }

    @Test
    fun `GIVEN no lifecycle and a recent background EXPECT saved session continued on first read`() {
        val id = sessionSentToBackground()
        nowMs += 10 * 60_000L

        assertEquals(id, repository().sessionId())
    }

    @Test
    fun `GIVEN no lifecycle and an old background EXPECT new session on first read`() {
        val id = sessionSentToBackground()
        nowMs += overTimeout

        assertNotEquals(id, repository().sessionId())
    }

    // Background

    @Test
    fun `GIVEN background over 30 minutes EXPECT new id`() {
        val repository = started()
        val id = repository.sessionId()

        repository.background(overTimeout)

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN background of exactly 30 minutes EXPECT same id`() {
        val repository = started()
        val id = repository.sessionId()

        repository.background(ClientSessionRepository.BACKGROUND_TIMEOUT_MS)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN short background EXPECT same id`() {
        val repository = started()
        val id = repository.sessionId()

        repository.background(60_000L)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN clock moved back EXPECT same id`() {
        val repository = started()
        val id = repository.sessionId()

        repository.background(-3_600_000L)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN repeated stop events EXPECT background counted from the first`() {
        val repository = started()
        val id = repository.sessionId()

        repository.onBackground()
        nowMs += 1_000_000L
        repository.onBackground()
        nowMs += 900_000L
        repository.onForeground()

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN two short backgrounds far apart EXPECT same id`() {
        val repository = started()
        val id = repository.sessionId()

        repository.background(20 * 60_000L)
        nowMs += 20 * 60_000L
        repository.background(20 * 60_000L)

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN background EXPECT background time persisted`() {
        val repository = started()
        nowMs += 60_000L

        repository.onBackground()

        assertEquals(nowMs, storage.clientSessionLastBackgroundAt)
    }

    // Logout

    @Test
    fun `GIVEN logout EXPECT new id saved`() {
        val repository = started()
        val id = repository.sessionId()

        repository.onLogout()

        assertNotEquals(id, repository.sessionId())
        assertEquals(repository.sessionId(), storage.clientSessionId)
    }

    @Test
    fun `GIVEN logout and a start within 30 minutes of the background EXPECT id from logout kept`() {
        val first = started()
        first.onLogout()
        first.onBackground()
        val id = first.sessionId()
        nowMs += 60_000L

        assertEquals(id, started(opened = false).sessionId())
    }

    // External mode

    @Test
    fun `GIVEN valid external id EXPECT kept as given`() {
        val repository = started()

        repository.setExternalSessionId("Host Session-1")

        assertEquals("Host Session-1", repository.sessionId())
    }

    @Test
    fun `GIVEN external id with surrounding spaces EXPECT trimmed id`() {
        val repository = started()

        repository.setExternalSessionId("  host-1 \t")

        assertEquals("host-1", repository.sessionId())
    }

    @Test
    fun `GIVEN empty external id EXPECT own id kept`() {
        val repository = started()
        val id = repository.sessionId()

        repository.setExternalSessionId("")

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN whitespace-only external id EXPECT own id kept`() {
        val repository = started()
        val id = repository.sessionId()

        repository.setExternalSessionId("   ")

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN external id with a line break inside EXPECT own id kept`() {
        val repository = started()
        val id = repository.sessionId()

        repository.setExternalSessionId("line\nbreak")

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN non-ASCII external id EXPECT own id kept`() {
        val repository = started()
        val id = repository.sessionId()

        repository.setExternalSessionId("сессия")

        assertEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN invalid external id EXPECT SDK keeps its own boundaries`() {
        val repository = started()
        val id = repository.sessionId()
        repository.setExternalSessionId("   ")

        repository.background(overTimeout)

        assertNotEquals(id, repository.sessionId())
    }

    @Test
    fun `GIVEN host id WHEN invalid id set EXPECT host id kept`() {
        val repository = started()
        repository.setExternalSessionId("host")

        repository.setExternalSessionId("")

        assertEquals("host", repository.sessionId())
    }

    @Test
    fun `GIVEN external mode and background over 30 minutes EXPECT host id and own session untouched`() {
        val repository = started()
        val ownId = storage.clientSessionId
        repository.setExternalSessionId("host")

        repository.background(overTimeout)

        assertEquals("host", repository.sessionId())
        assertEquals(ownId, storage.clientSessionId)
    }

    @Test
    fun `GIVEN external mode and logout EXPECT host id and own session untouched`() {
        val repository = started()
        val ownId = storage.clientSessionId
        repository.setExternalSessionId("host")

        repository.onLogout()

        assertEquals("host", repository.sessionId())
        assertEquals(ownId, storage.clientSessionId)
    }

    @Test
    fun `GIVEN external mode EXPECT background time not saved`() {
        val repository = started()
        val savedAt = storage.clientSessionLastBackgroundAt
        repository.setExternalSessionId("host")
        nowMs += 60_000L

        repository.onBackground()

        assertEquals(savedAt, storage.clientSessionLastBackgroundAt)
    }

    @Test
    fun `GIVEN external id set twice EXPECT latest id`() {
        val repository = started()
        repository.setExternalSessionId("host-1")

        repository.setExternalSessionId("host-2")

        assertEquals("host-2", repository.sessionId())
    }

    @Test
    fun `GIVEN external id before first foreground EXPECT own session not rotated`() {
        val repository = started(opened = false)
        val ownId = storage.clientSessionId
        repository.setExternalSessionId("host")

        repository.onForeground()

        assertEquals("host", repository.sessionId())
        assertEquals(ownId, storage.clientSessionId)
    }

    @Test
    fun `GIVEN external id before first foreground EXPECT host id kept`() {
        val repository = started(opened = false)
        repository.setExternalSessionId("host")

        nowMs += overTimeout
        repository.onForeground()

        assertEquals("host", repository.sessionId())
    }

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
