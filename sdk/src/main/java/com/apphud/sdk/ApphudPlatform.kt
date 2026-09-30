package com.apphud.sdk

import com.apphud.sdk.internal.ServiceLocator

/**
 * A user property key with extra attributes sent alongside the property.
 */
internal interface PlatformUserPropertyKeyDescribing {
    val name: String
    val attributes: Map<String, String>
}

internal interface PlatformProtocol {
    /**
     * Sets a user property whose key carries extra attributes. Otherwise behaves like
     * [Apphud.setUserProperty].
     */
    fun setUserProperty(key: PlatformUserPropertyKeyDescribing, value: Any?, setOnce: Boolean)

    /**
     * Returns the current session ID: the value the SDK puts in the `X-Apphud-Session-Id` header
     * of API requests it sends from now on.
     *
     * After [setSessionId] accepts an ID, returns the ID from the latest accepted call in this app
     * process; otherwise returns the SDK's own ID, which changes when the user opens the app, when the app
     * returns to the foreground after more than 30 minutes in the background, and on [Apphud.logout].
     *
     * Available before [Apphud.start].
     *
     * @return The session ID, or null if the SDK's init provider did not run.
     */
    fun sessionId(): String?

    /**
     * Sets the session ID for a host SDK that owns the session. Every subsequent API request
     * the SDK sends to Apphud carries this ID.
     *
     * Once an ID is accepted, the SDK stops starting sessions on its own: neither background nor
     * [Apphud.logout] changes the ID until another ID is accepted. The ID is not saved; after the app
     * process restarts the SDK starts its own sessions again until an ID is accepted.
     *
     * Call it before [Apphud.start] so that customer registration already carries this ID; a later call
     * affects only subsequent requests.
     *
     * @param sessionId The session ID; surrounding whitespace is trimmed. A blank value or one with
     * characters outside printable ASCII (line breaks, non-ASCII) is ignored and logged: the current
     * session stays.
     */
    fun setSessionId(sessionId: String)
}

internal object ApphudPlatform : PlatformProtocol {
    override fun setUserProperty(key: PlatformUserPropertyKeyDescribing, value: Any?, setOnce: Boolean) {
        runCatching {
            ApphudInternal.userPropertiesManager.setUserProperty(
                key = ApphudUserPropertyKey.CustomProperty(key.name),
                value = value,
                setOnce = setOnce,
                increment = false,
                attributes = key.attributes,
            )
        }.onFailure {
            ApphudLog.logE("setUserProperty: SDK not initialized. Call Apphud.start() first.")
        }
    }

    override fun sessionId(): String? =
        runCatching { ServiceLocator.instance.clientSessionRepository.sessionId() }.getOrNull()

    override fun setSessionId(sessionId: String) {
        runCatching { ServiceLocator.instance.clientSessionRepository.setExternalSessionId(sessionId) }
            .onFailure { ApphudLog.logE("setSessionId ignored: SDK is not initialized") }
    }
}
