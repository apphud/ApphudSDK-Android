package com.apphud.sdk

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
}
