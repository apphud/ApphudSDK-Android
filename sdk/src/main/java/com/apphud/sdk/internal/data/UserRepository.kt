package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for managing current user state
 * Provides thread-safe access to currentUser via Mutex
 */
internal class UserRepository(
    private val dataSource: UserDataSource
) {
    private val mutex = Mutex()

    @Volatile
    private var currentUser: ApphudUser? = null

    /**
     * Thread-safe read without lock thanks to @Volatile
     */
    fun getCurrentUser(): ApphudUser? {
        return currentUser
    }

    /**
     * Thread-safe write using Mutex
     *
     * @param saveToCache whether to save to cache (default true)
     * @return true if userId changed
     */
    suspend fun setCurrentUser(user: ApphudUser, saveToCache: Boolean = true): Boolean {
        return mutex.withLock {
            currentUser = user

            if (saveToCache) {
                dataSource.saveUser(user)
            } else {
                false
            }
        }
    }

    /**
     * Updates current user if null or userId differs
     *
     * @return true if user was updated
     */
    suspend fun updateUser(user: ApphudUser): Boolean {
        return mutex.withLock {
            val current = currentUser
            val shouldUpdate = current == null || current.userId != user.userId

            if (shouldUpdate) {
                currentUser = user
                dataSource.saveUser(user)
            } else {
                false
            }
        }
    }

    /**
     * Used during logout
     */
    suspend fun clearUser() {
        mutex.withLock {
            currentUser = null
            dataSource.clearUser()
        }
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
