package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser

/**
 * Repository for managing current user state
 * Thread-safe via @Volatile for reads and synchronized for writes
 */
internal class UserRepository(
    private val dataSource: UserDataSource
) {
    @Volatile
    private var currentUser: ApphudUser? = null

    /**
     * Thread-safe read thanks to @Volatile
     */
    fun getCurrentUser(): ApphudUser? {
        return currentUser
    }

    /**
     * Thread-safe write using synchronized
     *
     * @param saveToCache whether to save to cache (default true)
     * @return true if userId changed
     */
    @Synchronized
    fun setCurrentUser(user: ApphudUser, saveToCache: Boolean = true): Boolean {
        val userIdChanged = currentUser?.userId != user.userId
        currentUser = user

        if (saveToCache) {
            dataSource.saveUser(user)
        }

        return userIdChanged
    }

    /**
     * Updates current user if null or userId differs
     *
     * @return true if user was updated
     */
    @Synchronized
    fun updateUser(user: ApphudUser): Boolean {
        val current = currentUser
        val shouldUpdate = current == null || current.userId != user.userId

        if (shouldUpdate) {
            currentUser = user
            dataSource.saveUser(user)
        }

        return shouldUpdate
    }

    /**
     * Used during logout
     */
    @Synchronized
    fun clearUser() {
        currentUser = null
        dataSource.clearUser()
    }

    fun loadFromCache(): ApphudUser? {
        return dataSource.getCachedUser()
    }

    fun isCacheExpired(): Boolean {
        return dataSource.isCacheExpired()
    }

    fun isTemporaryUser(): Boolean {
        return currentUser?.isTemporary == true
    }

    fun userHasPurchases(): Boolean {
        val user = currentUser ?: return false
        return user.subscriptions.isNotEmpty() || user.purchases.isNotEmpty()
    }
}
