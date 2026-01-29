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

        // Preserve paywalls/placements if server returned empty ones.
        // This happens when /subscriptions endpoint is called (purchase verification)
        // which doesn't return paywalls, only subscription data.
        val existing = currentUser
        val mergedUser = if (user.paywalls.isEmpty() && existing?.paywalls?.isNotEmpty() == true) {
            user.copy(
                paywalls = existing.paywalls,
                placements = existing.placements
            )
        } else {
            user
        }

        currentUser = mergedUser

        if (mergedUser.isTemporary != true) {
            dataSource.saveUser(mergedUser)
        }

        return userIdChanged
    }

    @Synchronized
    fun clearUser() {
        currentUser = null
        dataSource.clearUser()
    }
}
