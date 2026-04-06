package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudError
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.store.SdkEvent
import com.apphud.sdk.internal.store.SdkState
import com.apphud.sdk.internal.store.Store
import kotlinx.coroutines.flow.first

internal class AwaitRegistrationUseCase(
    private val sdkStore: Store<SdkState, SdkEvent, *>,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke() {
        val currentState = sdkStore.state.value
        if (currentState is SdkState.NotInitialized) {
            throw ApphudError(MUST_REGISTER_ERROR)
        }

        val user = userRepository.getCurrentUser()
        if (user != null && user.isTemporary == false) return

        if (user?.isTemporary == true) {
            currentState.apiKey?.let {
                sdkStore.dispatch(SdkEvent.ForceRegistrationRequested(apiKey = it))
            }
        }

        sdkStore.state.first { it is SdkState.Ready || it is SdkState.Degraded }
        if (userRepository.getCurrentUser()?.isTemporary != false) {
            throw ApphudError("Registration failed")
        }
    }

    companion object {
        private const val MUST_REGISTER_ERROR =
            " :You must call `Apphud.start` method before calling any other methods."
    }
}
