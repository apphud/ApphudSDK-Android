package com.apphud.sdk.internal.data.local

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.storage.Storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class UserRepository(
    private val storage: Storage,
) {
    private val userMutex = Mutex()

    private val _currentUser = MutableSharedFlow<ApphudUser?>(replay = 1)
    val currentUserFlow: SharedFlow<ApphudUser?> = _currentUser.asSharedFlow()

    init {
        val cachedUser = storage.apphudUser
        _currentUser.tryEmit(cachedUser)
    }

    suspend fun getCurrentUser(): ApphudUser? {
        return userMutex.withLock {
            _currentUser.replayCache.first()
        }
    }

    suspend fun updateUser(user: ApphudUser) {
        userMutex.withLock {
            storage.apphudUser = user
            user.let { storage.userId = it.userId }
            _currentUser.tryEmit(user)
        }
    }


    suspend fun clearUser() {
        userMutex.withLock {
            storage.apphudUser = null
            storage.userId = null
            _currentUser.tryEmit(null)
        }
    }
} 