package com.apphud.sdk.internal.store

import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.ApphudUser

internal sealed class SdkEvent {
    data class StartInitialization(
        val apiKey: String,
        val userId: String?,
        val needRegistration: Boolean,
        val isNew: Boolean,
        val cachedUser: ApphudUser?,
    ) : SdkEvent()

    data class RegistrationSucceeded(val user: ApphudUser) : SdkEvent()
    data class RegistrationFailed(val error: ApphudError, val cachedUser: ApphudUser?) : SdkEvent()

    data class ForceRegistrationRequested(
        val apiKey: String,
        val userId: String? = null,
        val email: String? = null,
    ) : SdkEvent()

    data class FallbackLoaded(val user: ApphudUser) : SdkEvent()
    object FallbackDisabled : SdkEvent()

    object RetryRegistration : SdkEvent()

    object SessionCleared : SdkEvent()
}
