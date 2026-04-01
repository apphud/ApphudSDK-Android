package com.apphud.sdk.internal.store

import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.ApphudUser

internal sealed class SdkState {
    object NotInitialized : SdkState()

    data class Registering(
        val apiKey: String,
        val userId: String?,
        val isForce: Boolean = false,
        val isNew: Boolean = true,
    ) : SdkState()

    data class Ready(
        val apiKey: String,
        val user: ApphudUser,
        val fromFallback: Boolean = false,
    ) : SdkState()

    data class Degraded(
        val apiKey: String,
        val user: ApphudUser?,
        val lastError: ApphudError?,
        val retryCount: Int = 0,
        val fromFallback: Boolean = false,
    ) : SdkState()
}
