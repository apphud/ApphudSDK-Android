package com.apphud.sdk.internal.domain

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
        if (ApphudUtils.optOutOfTracking) return false
        val old = repository.getIdentifiers()
        val new = repository.fetchAndUpdateIdentifiers()
        return old != new
    }
}
