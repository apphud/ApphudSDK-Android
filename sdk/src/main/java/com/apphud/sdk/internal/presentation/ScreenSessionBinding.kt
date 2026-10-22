package com.apphud.sdk.internal.presentation

import com.apphud.sdk.internal.SessionComponent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/** Rejects intents restored after process death or a logout/start cycle. */
internal class ScreenSessionBinding private constructor(
    val session: SessionComponent,
    private val sessions: StateFlow<SessionComponent?>,
) {
    val isCurrent: Boolean
        get() = sessions.value === session && session.coroutineScope.isActive

    suspend fun awaitEnd() {
        sessions.first { it !== session }
    }

    companion object {
        fun create(sessions: StateFlow<SessionComponent?>, screenSessionId: String?): ScreenSessionBinding? {
            val session = sessions.value ?: return null
            if (screenSessionId != session.screenSessionId || !session.coroutineScope.isActive) return null
            return ScreenSessionBinding(session, sessions)
        }
    }
}
