package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.domain.model.ApphudIdentity
import com.apphud.sdk.internal.domain.model.RequestIds

/**
 * Repository for managing current user state
 * Thread-safe with in-memory cache and storage persistence
 */
internal class UserRepository(
    private val dataSource: UserDataSource,
    private val identityStore: IdentityStore,
    /** Identity version 1 runs: the ids of SDK 3.6. */
    private val isVersion1: () -> Boolean = { false },
) {
    private var currentUser: ApphudUser? = null

    /** Identity version 1: the user id set before its customer came back, as SDK 3.6 kept it. */
    private var pendingUserId: String? = null

    /** The ids resolved at start; null before start and with identity version 1. */
    fun getIdentity(): ApphudIdentity? = identityStore.current

    /**
     * The user id once identified, the anonymous id before; null before start. Identity version 1:
     * its user id, as SDK 3.6 had it.
     */
    @Synchronized
    fun getUserId(): String? =
        if (isVersion1()) {
            currentUser?.userId ?: pendingUserId ?: dataSource.getCachedUser()?.userId
        } else {
            identityStore.current?.currentId
        }

    /** Identity version 1: the user id the next registration sends. */
    @Synchronized
    fun setUserId(id: String) {
        pendingUserId = id
    }

    /** Identity version 1: the device id. */
    fun getDeviceId(): String? = dataSource.getDeviceId()

    /** Identity version 1: the device id. */
    fun setDeviceId(id: String) = dataSource.saveDeviceId(id)

    /** The user id the app set by identify; null while anonymous. */
    fun getIdentifiedUserId(): String? = identityStore.current?.userId

    /** The ids requests carry; null before start. */
    fun getRequestIds(): RequestIds? {
        if (!isVersion1()) return identityStore.current?.requestIds
        val deviceId = getDeviceId() ?: return null
        val userId = getUserId() ?: return null
        return RequestIds(deviceId = deviceId, userId = userId)
    }

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
        pendingUserId = null

        if (mergedUser.isTemporary != true) {
            dataSource.saveUser(mergedUser)
        }

        return userIdChanged
    }

    @Synchronized
    fun clearUser() {
        currentUser = null
        pendingUserId = null
        dataSource.clearUser()
    }
}
