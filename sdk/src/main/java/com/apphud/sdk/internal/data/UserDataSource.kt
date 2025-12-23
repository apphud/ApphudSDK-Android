package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.storage.SharedPreferencesStorage

/**
 * Data source for user caching operations
 * Encapsulates SharedPreferences access
 */
internal class UserDataSource(
    private val storage: SharedPreferencesStorage
) {
    fun getCachedUser(): ApphudUser? {
        return storage.apphudUser
    }

    /**
     * @return true if userId changed
     */
    fun saveUser(user: ApphudUser): Boolean {
        return storage.updateUser(user)
    }

    fun clearUser() {
        storage.apphudUser = null
        storage.userId = null
    }

    fun getUserId(): String? {
        return storage.userId
    }

    fun saveUserId(userId: String) {
        storage.userId = userId
    }

    fun getDeviceId(): String? {
        return storage.deviceId
    }

    fun saveDeviceId(deviceId: String) {
        storage.deviceId = deviceId
    }

    fun isCacheExpired(): Boolean {
        return storage.cacheExpired()
    }

    fun updateLastRegistrationTime(timestamp: Long) {
        storage.lastRegistration = timestamp
    }
}
