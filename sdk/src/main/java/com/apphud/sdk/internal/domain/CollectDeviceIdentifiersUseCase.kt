package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository

internal class CollectDeviceIdentifiersUseCase(
    private val repository: DeviceIdentifiersRepository,
) {

    /**
     * Fetches device identifiers and compares with cached values.
     * @return true if identifiers changed (or were fetched for the first time)
     */
    suspend operator fun invoke(): Boolean {
        if (ApphudUtils.optOutOfTracking) {
            ApphudLog.logI("$TAG: optOutOfTracking=true, skipping")
            return false
        }
        val old = repository.getIdentifiers()
        ApphudLog.logI("$TAG: cached=$old")
        val new = repository.fetchAndUpdateIdentifiers()
        ApphudLog.logI("$TAG: fetched=$new")
        val changed = old != new
        ApphudLog.logI("$TAG: changed=$changed")
        return changed
    }

    private companion object {
        const val TAG = "CollectDeviceIdentifiersUseCase"
    }
}
