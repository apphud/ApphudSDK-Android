package com.apphud.sdk.internal.store

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.SdkRegistrationState
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.UserPropertiesManager
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.domain.FetchNativePurchasesUseCase
import com.apphud.sdk.internal.domain.RegistrationInteractor
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.apphud.sdk.toApphudError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SdkEffectHandler(
    private val registrationInteractor: RegistrationInteractor,
    private val userRepository: UserRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val userPropertiesManager: UserPropertiesManager,
    private val fetchNativePurchasesUseCase: FetchNativePurchasesUseCase,
    private val storage: SharedPreferencesStorage,
    private val coroutineScope: CoroutineScope,
    private val registrationState: SdkRegistrationState,
) {
    private val registrationMutex = Mutex()

    suspend fun handle(effect: SdkEffect, dispatch: (SdkEvent) -> Unit) {
        when (effect) {
            is SdkEffect.PerformRegistration -> registrationMutex.withLock {
                performRegistration(effect, dispatch)
            }
        }
    }

    private suspend fun performRegistration(effect: SdkEffect.PerformRegistration, dispatch: (SdkEvent) -> Unit) {
        registrationState.markRegistrationStarted()
        val needPlacementsPaywalls = !registrationState.observerMode &&
            !registrationState.deferPlacements &&
            (effect.isForce || !registrationState.didRegisterCustomerAtThisLaunch)

        runCatchingCancellable {
            registrationInteractor(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = effect.isNew,
                forceRegistration = effect.isForce,
                userId = effect.userId,
                email = effect.email,
            )
        }.onSuccess { user ->
            analyticsTracker.recordFirstCustomerLoaded()
            registrationState.markPaywallsResponded(needPlacementsPaywalls)

            if (storage.isNeedSync) {
                coroutineScope.launch {
                    runCatchingCancellable { fetchNativePurchasesUseCase() }
                        .onFailure { ApphudLog.logE("isNeedSync sync failed: ${it.message}") }
                }
            }

            registrationState.markRegistrationFinished()
            dispatch(SdkEvent.RegistrationSucceeded(user))

            runCatchingCancellable { userPropertiesManager.flushIfNeeded() }
        }.onFailure { error ->
            registrationState.markRegistrationFinished()
            val cachedUser = userRepository.getCurrentUser()
            dispatch(SdkEvent.RegistrationFailed(error.toApphudError(), cachedUser))
        }
    }
}
