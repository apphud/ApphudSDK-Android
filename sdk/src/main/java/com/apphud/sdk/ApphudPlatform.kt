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
     * Current session ID sent in the `X-Apphud-Session-Id` header: the one from [setSessionId],
     * or the SDK's own. Null if the SDK's init provider did not run.
     */
    fun sessionId(): String?

    /**
     * Sets the session ID for all subsequent requests; the SDK stops rotating sessions until the
     * app restarts.
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
