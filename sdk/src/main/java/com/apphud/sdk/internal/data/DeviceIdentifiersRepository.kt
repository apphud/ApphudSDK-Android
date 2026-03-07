package com.apphud.sdk.internal.data

import com.apphud.sdk.internal.domain.model.DeviceIdentifiers

internal class DeviceIdentifiersRepository(
    private val dataSource: DeviceIdentifiersDataSource,
) {

    fun getIdentifiers(): DeviceIdentifiers = dataSource.loadCached()

    suspend fun fetchAndUpdateIdentifiers(): DeviceIdentifiers {
        val identifiers = dataSource.fetchIdentifiers()
        dataSource.save(identifiers)
        return identifiers
    }

    fun fetchAndroidIdSync(): String? = dataSource.fetchAndroidIdSync()

    fun clear() {
        dataSource.save(DeviceIdentifiers.EMPTY)
    }
}
