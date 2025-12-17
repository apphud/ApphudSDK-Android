package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser

/**
 * Repository for managing current user state
 * Thread-safe with in-memory cache and storage persistence
 */
internal class UserRepository(
    private val dataSource: UserDataSource
) {
    private var currentUser: ApphudUser? = null

    @Synchronized
    fun getCurrentUser(): ApphudUser? {
        return currentUser ?: dataSource.getCachedUser()
    }

    /**
     * @return true if userId changed
     */
    @Synchronized
    fun setCurrentUser(user: ApphudUser): Boolean {
        val userIdChanged = currentUser?.userId != user.userId
        currentUser = user

        if (user.isTemporary != true) {
            dataSource.saveUser(user)
        }

        return userIdChanged
    }

    @Synchronized
    fun clearUser() {
        currentUser = null
        dataSource.clearUser()
    }
}
