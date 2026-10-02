package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.storage.ClientSessionStorage
import java.util.UUID

/**
 * Client-side session, sent as the `X-Apphud-Session-Id` header on every request.
 *
 * Default mode:
 * - The first ON_START in a process starts a new session, even within 30 minutes of the last
 *   background: a start by the user can't be told from a background one at process start (a
 *   widget bind looks like a user start), so the first foreground stands for it.
 * - Before that (at process start, or in a background-only process: push, WorkManager, a widget
 *   bind) the saved session continues if the app went to the background at most 30 minutes ago,
 *   otherwise a new one starts, in the background since then. Requests sent before the first
 *   ON_START carry this session; the registration from `Apphud.start` on a cold start runs
 *   asynchronously and may go out before or after it.
 * - Back from the background after more than 30 minutes: a new session; logout: a new session.
 * External mode ([setExternalSessionId]): the host owns every boundary until the process ends,
 * and nothing is saved.
 */
internal class ClientSessionRepository(
    private val storage: ClientSessionStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var ownId: String? = null
    private var externalId: String? = null
    private var sessionStartedInProcess = false
    private var foregroundSeen = false

    // When the app went to the background, while it is there.
    private var backgroundStartedAt: Long? = null

    /** Without the lifecycle (attach failed), the first read applies the start rule. */
    @Synchronized
    fun sessionId(): String {
        onProcessStart()
        return externalId ?: loadOwnId()
    }

    /**
     * The process started. Continues the saved session and keeps its background time, so starts
     * never extend it; otherwise starts a new session, in the background since now.
     */
    @Synchronized
    fun onProcessStart() {
        if (sessionStartedInProcess) return
        sessionStartedInProcess = true
        val startedAt = now()
        val savedId = storage.clientSessionId
        val lastBackgroundAt = storage.clientSessionLastBackgroundAt
        if (savedId != null && lastBackgroundAt > 0L && startedAt - lastBackgroundAt in 0L..BACKGROUND_TIMEOUT_MS) {
            ownId = savedId
            backgroundStartedAt = lastBackgroundAt
            return
        }
        backgroundStartedAt = startedAt
        if (externalId == null) {
            startNewSession()
            storage.clientSessionLastBackgroundAt = startedAt
        }
    }

    /** Process lifecycle ON_START. */
    @Synchronized
    fun onForeground() {
        onProcessStart()
        if (!foregroundSeen) {
            // The first foreground in the process stands for a start by the user.
            foregroundSeen = true
            backgroundStartedAt = null
            if (externalId == null) startNewSession()
            return
        }
        val startedAt = backgroundStartedAt ?: return
        backgroundStartedAt = null
        if (externalId == null && now() - startedAt > BACKGROUND_TIMEOUT_MS) startNewSession()
    }

    /** Process lifecycle ON_STOP. */
    @Synchronized
    fun onBackground() {
        // The first event wins: the app has been in the background since then.
        if (backgroundStartedAt != null) return
        val startedAt = now()
        backgroundStartedAt = startedAt
        if (externalId == null) storage.clientSessionLastBackgroundAt = startedAt
    }

    @Synchronized
    fun onLogout() {
        if (externalId == null) startNewSession()
    }

    /** A blank value or one that can't be an HTTP header value is ignored: the session stays. */
    @Synchronized
    fun setExternalSessionId(sessionId: String) {
        val id = sessionId.trim()
        if (id.isEmpty() || id.any { it !in ' '..'~' }) {
            ApphudLog.logE("setSessionId ignored: invalid session id")
            return
        }
        externalId = id
    }

    private fun loadOwnId(): String =
        ownId ?: (storage.clientSessionId ?: newId().also { storage.clientSessionId = it })
            .also { ownId = it }

    private fun startNewSession() {
        val id = newId()
        ownId = id
        storage.clientSessionId = id
    }

    companion object {
        const val BACKGROUND_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
