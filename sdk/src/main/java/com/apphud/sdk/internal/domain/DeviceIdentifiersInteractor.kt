package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

internal class DeviceIdentifiersInteractor(
    private val collectUseCase: CollectDeviceIdentifiersUseCase,
    private val registrationInteractor: RegistrationInteractor,
    private val deviceIdentifiersRepository: DeviceIdentifiersRepository,
    private val userRepository: UserRepository,
) {

    suspend operator fun invoke(
        scope: CoroutineScope,
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
    ): ApphudUser? {
        val startTime = System.currentTimeMillis()
        ApphudLog.logI("$TAG: Started")

        val userId = userRepository.getUserId()
        val fetchDeferred = scope.async { collectUseCase() }
        val fetchedInTime = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchDeferred.await() } != null
        ApphudLog.logI("$TAG: collectUseCase fetchedInTime=$fetchedInTime [${elapsed(startTime)}]")

        // Skip early registration when there's nothing useful to send yet (first install / cleared cache).
        // It would just be a wasted /customers POST with empty IDs that the late call will replace anyway.
        val cachedIds = deviceIdentifiersRepository.getIdentifiers()
        val haveCachedIds = cachedIds != DeviceIdentifiers.EMPTY
        if (!fetchedInTime && haveCachedIds && needsRegistration(userId, cachedIds)) {
            ApphudLog.logI("$TAG: Timeout, calling early registrationInteractor [${elapsed(startTime)}], cached ids: $cachedIds")
            registerAndMarkSynced(
                userId = userId,
                identifiers = cachedIds,
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                startTime = startTime,
                phase = "Early",
            )
        } else if (!fetchedInTime && haveCachedIds) {
            ApphudLog.logI("$TAG: Timeout, identifiers already synced for user, skipping early registration [${elapsed(startTime)}]")
        } else if (!fetchedInTime) {
            ApphudLog.logI("$TAG: Timeout, but no cached IDs to send, skipping early registration [${elapsed(startTime)}]")
        }

        val changed = fetchDeferred.await()
        val currentIds = deviceIdentifiersRepository.getIdentifiers()
        ApphudLog.logI("$TAG: collectUseCase completed, changed=$changed, ids=$currentIds [${elapsed(startTime)}]")

        if (!changed && currentIds == DeviceIdentifiers.EMPTY) {
            ApphudLog.logI("$TAG: Identifiers are empty and unchanged, returning null [${elapsed(startTime)}]")
            return null
        }

        if (changed) {
            ApphudLog.logI("$TAG: Identifiers changed, calling final registrationInteractor [${elapsed(startTime)}]")
            return registerAndMarkSynced(
                userId = userId,
                identifiers = currentIds,
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                startTime = startTime,
                phase = "Final",
            )
        }

        if (!needsRegistration(userId, currentIds)) {
            ApphudLog.logI("$TAG: Identifiers already synced for user, returning null [${elapsed(startTime)}]")
            return null
        }

        ApphudLog.logI("$TAG: Calling final registrationInteractor [${elapsed(startTime)}]")
        return registerAndMarkSynced(
            userId = userId,
            identifiers = currentIds,
            needPlacementsPaywalls = needPlacementsPaywalls,
            isNew = isNew,
            startTime = startTime,
            phase = "Final",
        )
    }

    private suspend fun registerAndMarkSynced(
        userId: String?,
        identifiers: DeviceIdentifiers,
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
        startTime: Long,
        phase: String,
    ): ApphudUser {
        val user = registrationInteractor(
            needPlacementsPaywalls = needPlacementsPaywalls,
            isNew = isNew,
            forceRegistration = true,
        )
        userId?.let { deviceIdentifiersRepository.markSynced(it, identifiers) }
        ApphudLog.logI("$TAG: $phase registrationInteractor completed [${elapsed(startTime)}]")
        return user
    }

    private fun needsRegistration(userId: String?, identifiers: DeviceIdentifiers): Boolean =
        !deviceIdentifiersRepository.isSyncedForUser(userId, identifiers)

    private fun elapsed(startTime: Long): String = "${System.currentTimeMillis() - startTime}ms"

    private companion object {
        const val TAG = "DeviceIdentifiersInteractor"
        const val FETCH_TIMEOUT_MS = 1000L
    }
}
