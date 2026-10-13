package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.domain.model.ApphudIdentity

/**
 * Repository for managing current user state
 * Thread-safe with in-memory cache and storage persistence
 */
internal class UserRepository(
    private val dataSource: UserDataSource,
    private val identityStore: IdentityStore,
) {
    private var currentUser: ApphudUser? = null

    /** The ids resolved at start; null before start. */
    fun getIdentity(): ApphudIdentity? = identityStore.current

    /** The user id once identified, the anonymous id before; null before start. */
    fun getUserId(): String? = identityStore.current?.currentId

    /** The user id the app set by identify; null while anonymous. */
    fun getIdentifiedUserId(): String? = identityStore.current?.userId

    @Synchronized
    fun getCurrentUser(): ApphudUser? {
        return currentUser ?: dataSource.getCachedUser()
    }

    @Synchronized
    fun getInternalId(): String? = getCurrentUser()?.internalId

    /**
     * @return true if userId changed
     */
    @Synchronized
    fun setCurrentUser(user: ApphudUser): Boolean {
        val userIdChanged = currentUser?.userId != user.userId

        // Merges placements if server returned empty ones.
        // This happens when /subscriptions endpoint is called (purchase verification)
        // which doesn't return placements, only subscription data.
        val existing = currentUser
        val mergedUser = if (user.placements.isEmpty() && existing?.placements?.isNotEmpty() == true) {
            user.copy(placements = existing.placements)
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
