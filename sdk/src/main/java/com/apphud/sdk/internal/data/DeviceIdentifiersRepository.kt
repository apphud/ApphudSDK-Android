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
        dataSource.clearSyncedState()
    }

    fun isSyncedForUser(userId: String?, identifiers: DeviceIdentifiers): Boolean {
        if (userId.isNullOrEmpty() || identifiers == DeviceIdentifiers.EMPTY) return false
        val synced = dataSource.loadSyncedState() ?: return false
        return synced.userId == userId && synced.identifiers == identifiers
    }

    fun markSynced(userId: String, identifiers: DeviceIdentifiers) {
        dataSource.saveSyncedState(userId, identifiers)
    }
}
