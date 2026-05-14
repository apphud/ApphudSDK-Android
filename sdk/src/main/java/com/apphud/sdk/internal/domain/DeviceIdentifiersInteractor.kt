package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

internal class DeviceIdentifiersInteractor(
    private val collectUseCase: CollectDeviceIdentifiersUseCase,
    private val registrationUseCase: RegistrationUseCase,
    private val deviceIdentifiersRepository: DeviceIdentifiersRepository,
) {

    suspend operator fun invoke(
        scope: CoroutineScope,
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
    ): ApphudUser? {
        val startTime = System.currentTimeMillis()
        ApphudLog.logI("$TAG: Started")

        val fetchDeferred = scope.async { collectUseCase() }
        val fetchedInTime = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchDeferred.await() } != null
        ApphudLog.logI("$TAG: collectUseCase fetchedInTime=$fetchedInTime [${elapsed(startTime)}]")

        // Skip early registration when there's nothing useful to send yet (first install / cleared cache).
        // It would just be a wasted /customers POST with empty IDs that the late call will replace anyway.
        val haveCachedIds = deviceIdentifiersRepository.getIdentifiers() != DeviceIdentifiers.EMPTY
        if (!fetchedInTime && haveCachedIds) {
            ApphudLog.logI("$TAG: Timeout, calling early registrationUseCase [${elapsed(startTime)}]")
            registrationUseCase(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = true,
            )
            ApphudLog.logI("$TAG: Early registrationUseCase completed [${elapsed(startTime)}]")
        } else if (!fetchedInTime) {
            ApphudLog.logI("$TAG: Timeout, but no cached IDs to send, skipping early registrationUseCase [${elapsed(startTime)}]")
        }

        val changed = fetchDeferred.await()
        ApphudLog.logI("$TAG: collectUseCase completed, changed=$changed [${elapsed(startTime)}]")
        if (!changed) {
            ApphudLog.logI("$TAG: No changes, returning null [${elapsed(startTime)}]")
            return null
        }

        ApphudLog.logI("$TAG: Changes detected, calling final registrationUseCase [${elapsed(startTime)}]")
        val user = registrationUseCase(
            needPlacementsPaywalls = needPlacementsPaywalls,
            isNew = isNew,
            forceRegistration = true,
        )
        ApphudLog.logI("$TAG: Final registrationUseCase completed [${elapsed(startTime)}]")
        return user
    }

    private fun elapsed(startTime: Long): String = "${System.currentTimeMillis() - startTime}ms"

    private companion object {
        const val TAG = "DeviceIdentifiersInteractor"
        const val FETCH_TIMEOUT_MS = 1000L
    }
}
