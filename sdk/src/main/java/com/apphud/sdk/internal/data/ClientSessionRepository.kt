package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.storage.ClientSessionStorage
import java.util.UUID

/**
 * Client-side session, sent as the `X-Apphud-Session-Id` header on every request.
 *
 * Default mode: a session starts when the user opens the app (a background-only process
 * start never opens one), on return to the foreground after more than 30 minutes in the
 * background, and on logout. The id is persisted so a background-only process keeps the
 * previous session. External mode ([setExternalSessionId]): the host owns every boundary
 * until the process ends.
 */
internal class ClientSessionRepository(
    private val storage: ClientSessionStorage,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var ownId: String? = null
    private var externalId: String? = null
    private var sessionStartedInProcess = false

    // This process only: the launch boundary already starts a new session, so the persisted
    // value is never compared.
    private var backgroundStartedAt: Long? = null

    @Synchronized
    fun sessionId(): String = externalId ?: loadOwnId()

    @Synchronized
    fun sessionNumber(): Int = storage.clientSessionNumber

    /** The user opened the app. */
    @Synchronized
    fun onAppOpened() {
        if (sessionStartedInProcess) return
        sessionStartedInProcess = true
        if (externalId == null) startNewSession()
    }

    /**
     * The process looks started for the user. The app is not visible until the first ON_START:
     * if that takes more than 30 minutes (the process was only bound by the top app), that
     * first real open starts a new session.
     */
    @Synchronized
    fun onOpenedAtProcessStart() {
        onAppOpened()
        if (backgroundStartedAt == null) backgroundStartedAt = now()
    }

    /** Process lifecycle ON_START. */
    @Synchronized
    fun onForeground() {
        if (!sessionStartedInProcess) {
            onAppOpened()
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
        storage.clientSessionLastBackgroundAt = startedAt
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
        storage.clientSessionNumber = storage.clientSessionNumber + 1
    }

    companion object {
        const val BACKGROUND_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
