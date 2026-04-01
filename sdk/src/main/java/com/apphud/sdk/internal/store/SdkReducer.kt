package com.apphud.sdk.internal.store

internal fun sdkReducer(
    state: SdkState,
    event: SdkEvent,
): Pair<SdkState, List<SdkEffect>> = when (state) {

    is SdkState.NotInitialized -> when (event) {
        is SdkEvent.StartInitialization -> {
            if (event.needRegistration) {
                SdkState.Registering(event.apiKey, event.userId, isNew = event.isNew) to
                    listOf(SdkEffect.PerformRegistration(isForce = false, isNew = event.isNew))
            } else {
                val user = event.cachedUser
                if (user != null) {
                    SdkState.Ready(event.apiKey, user) to emptyList()
                } else {
                    SdkState.Registering(event.apiKey, event.userId, isNew = event.isNew) to
                        listOf(SdkEffect.PerformRegistration(isForce = false, isNew = event.isNew))
                }
            }
        }
        else -> state to emptyList()
    }

    is SdkState.Registering -> when (event) {
        is SdkEvent.RegistrationSucceeded -> {
            SdkState.Ready(state.apiKey, event.user) to emptyList()
        }
        is SdkEvent.RegistrationFailed -> {
            SdkState.Degraded(
                apiKey = state.apiKey,
                user = event.cachedUser,
                lastError = event.error,
                retryCount = 0,
                fromFallback = event.cachedUser?.isTemporary == true,
            ) to emptyList()
        }
        is SdkEvent.FallbackLoaded -> {
            SdkState.Ready(state.apiKey, event.user, fromFallback = true) to emptyList()
        }
        is SdkEvent.SessionCleared -> SdkState.NotInitialized to emptyList()
        else -> state to emptyList()
    }

    is SdkState.Ready -> when (event) {
        is SdkEvent.RegistrationSucceeded -> {
            SdkState.Ready(state.apiKey, event.user, fromFallback = false) to emptyList()
        }
        is SdkEvent.ForceRegistrationRequested -> {
            SdkState.Registering(event.apiKey, event.userId, isForce = true, isNew = false) to
                listOf(SdkEffect.PerformRegistration(isForce = true, isNew = false, userId = event.userId, email = event.email))
        }
        is SdkEvent.FallbackDisabled -> {
            state.copy(fromFallback = false) to emptyList()
        }
        is SdkEvent.SessionCleared -> {
            SdkState.NotInitialized to emptyList()
        }
        else -> state to emptyList()
    }

    is SdkState.Degraded -> when (event) {
        is SdkEvent.ForceRegistrationRequested -> {
            SdkState.Registering(event.apiKey, event.userId, isForce = true, isNew = false) to
                listOf(SdkEffect.PerformRegistration(isForce = true, isNew = false, userId = event.userId, email = event.email))
        }
        is SdkEvent.RetryRegistration -> {
            SdkState.Registering(state.apiKey, null, isForce = true, isNew = false) to
                listOf(SdkEffect.PerformRegistration(isForce = true, isNew = false))
        }
        is SdkEvent.RegistrationSucceeded -> {
            SdkState.Ready(state.apiKey, event.user) to emptyList()
        }
        is SdkEvent.FallbackLoaded -> {
            SdkState.Ready(state.apiKey, event.user, fromFallback = true) to emptyList()
        }
        is SdkEvent.SessionCleared -> {
            SdkState.NotInitialized to emptyList()
        }
        else -> state to emptyList()
    }
}
