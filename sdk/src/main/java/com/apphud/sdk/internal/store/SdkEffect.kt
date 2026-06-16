package com.apphud.sdk.internal.store

internal sealed class SdkEffect {
    data class PerformRegistration(
        val isForce: Boolean,
        val isNew: Boolean = true,
        val userId: String? = null,
        val email: String? = null,
    ) : SdkEffect()
}
