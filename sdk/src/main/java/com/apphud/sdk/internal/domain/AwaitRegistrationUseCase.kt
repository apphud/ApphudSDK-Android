package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudError
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.store.SdkEvent
import com.apphud.sdk.internal.store.SdkState
import com.apphud.sdk.internal.store.Store
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
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
            coroutineScope {
                val nextTerminalState = async(start = CoroutineStart.UNDISPATCHED) {
                    sdkStore.state.drop(1).first { it.isTerminal() }
                }
                currentState.apiKey ?: throw ApphudError(MUST_REGISTER_ERROR)
                sdkStore.dispatch(SdkEvent.ForceRegistrationRequested())
                nextTerminalState.await()
            }
        } else {
            sdkStore.state.first { it.isTerminal() }
        }

        if (userRepository.getCurrentUser()?.isTemporary != false) {
            throw ApphudError("Registration failed")
        }
    }

    private fun SdkState.isTerminal(): Boolean =
        this is SdkState.Ready || this is SdkState.Degraded

    companion object {
        private const val MUST_REGISTER_ERROR =
            " :You must call `Apphud.start` method before calling any other methods."
    }
}
