package com.apphud.sdk.internal.store

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.SdkRegistrationState
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.UserPropertiesManager
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.domain.FetchNativePurchasesUseCase
import com.apphud.sdk.internal.domain.RegistrationUseCase
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.apphud.sdk.toApphudError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SdkEffectHandler(
    private val registrationUseCase: RegistrationUseCase,
    private val userRepository: UserRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val userPropertiesManager: UserPropertiesManager,
    private val fetchNativePurchasesUseCase: FetchNativePurchasesUseCase,
    private val storage: SharedPreferencesStorage,
    private val coroutineScope: CoroutineScope,
    private val registrationState: SdkRegistrationState,
) {
    suspend fun handle(effect: SdkEffect, dispatch: (SdkEvent) -> Unit) {
        when (effect) {
            is SdkEffect.PerformRegistration -> performRegistration(effect, dispatch)
        }
    }

    private suspend fun performRegistration(effect: SdkEffect.PerformRegistration, dispatch: (SdkEvent) -> Unit) {
        registrationState.isRegisteringUser = true
        val needPP = !registrationState.didRegisterCustomerAtThisLaunch &&
            !registrationState.deferPlacements &&
            !registrationState.observerMode

        runCatchingCancellable {
            registrationUseCase(
                needPlacementsPaywalls = needPP,
                isNew = effect.isNew,
                forceRegistration = effect.isForce,
                userId = effect.userId,
                email = effect.email,
            )
        }.onSuccess { user ->
            analyticsTracker.recordFirstCustomerLoaded()
            registrationState.hasRespondedToPaywallsRequest = needPP

            if (storage.isNeedSync) {
                coroutineScope.launch {
                    runCatchingCancellable { fetchNativePurchasesUseCase() }
                        .onFailure { ApphudLog.logE("isNeedSync sync failed: ${it.message}") }
                }
            }

            registrationState.isRegisteringUser = false
            dispatch(SdkEvent.RegistrationSucceeded(user))

            runCatchingCancellable { userPropertiesManager.flushIfNeeded() }
        }.onFailure { error ->
            registrationState.isRegisteringUser = false
            val cachedUser = userRepository.getCurrentUser()
            dispatch(SdkEvent.RegistrationFailed(error.toApphudError(), cachedUser))
        }
    }
}
