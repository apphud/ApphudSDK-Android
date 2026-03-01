package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

internal class DeviceIdentifiersInteractor(
    private val collectUseCase: CollectDeviceIdentifiersUseCase,
    private val registrationUseCase: RegistrationUseCase,
) {

    suspend operator fun invoke(
        scope: CoroutineScope,
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
    ): ApphudUser? {
        val fetchDeferred = scope.async { collectUseCase() }
        val fetchedInTime = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchDeferred.await() } != null

        if (!fetchedInTime) {
            registrationUseCase(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = true,
            )
        }

        val changed = fetchDeferred.await()
        if (!changed) return null

        return registrationUseCase(
            needPlacementsPaywalls = needPlacementsPaywalls,
            isNew = isNew,
            forceRegistration = true,
        )
    }

    private companion object {
        const val FETCH_TIMEOUT_MS = 1000L
    }
}
