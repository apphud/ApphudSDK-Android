package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserDataSource
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.toApphudError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UseCase for user registration
 * Combines normal and force registration with shared Mutex to prevent race conditions
 */
internal class RegistrationUseCase(
    private val userRepository: UserRepository,
    private val userDataSource: UserDataSource,
    private val requestManager: RequestManager,
) {
    private val mutex = Mutex()

    /**
     * @param needPlacementsPaywalls whether to load paywalls and placements
     * @param isNew flag for new user
     * @param forceRegistration force registration (ignores cache)
     * @param userId optional userId for user switching
     * @param email optional email for update
     * @throws ApphudError if registration fails
     */
    suspend operator fun invoke(
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
        userId: String? = null,
        email: String? = null,
    ): ApphudUser =
        mutex.withLock {
            if (!forceRegistration) {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null && currentUser.isTemporary == false) {
                    ApphudLog.log("Registration: User already loaded, returning cached user")
                    return@withLock currentUser
                }
            }

            performRegistration(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = forceRegistration,
                userId = userId,
                email = email
            )
        }

    private suspend fun performRegistration(
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean,
        userId: String? = null,
        email: String? = null,
    ): ApphudUser {
        val registrationType = if (forceRegistration) "Force Registration" else "Registration"
        ApphudLog.log(
            "$registrationType: needPlacementsPaywalls=$needPlacementsPaywalls, " +
                "isNew=$isNew, userId=$userId, email=$email"
        )

        val cachedUser = userRepository.getCurrentUser()

        val newUser = runCatchingCancellable {
            requestManager.registration(
                needPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = forceRegistration,
                userId = userId,
                email = email
            )
        }.getOrElse { error ->
            ApphudLog.logE("$registrationType failed: ${error.message}")
            throw error.toApphudError()
        }

        val finalUser = mergePaywallsAndPlacements(newUser, cachedUser)

        userRepository.setCurrentUser(finalUser)
        userDataSource.updateLastRegistrationTime(System.currentTimeMillis())

        ApphudLog.log("$registrationType successful: userId=${finalUser.userId}")

        return finalUser
    }

    private fun mergePaywallsAndPlacements(
        newUser: ApphudUser,
        cachedUser: ApphudUser?,
    ): ApphudUser {
        if (cachedUser == null) return newUser

        val shouldPreservePaywalls = newUser.paywalls.isEmpty() && cachedUser.paywalls.isNotEmpty()
        val shouldPreservePlacements = newUser.placements.isEmpty() && cachedUser.placements.isNotEmpty()

        return if (shouldPreservePaywalls || shouldPreservePlacements) {
            ApphudLog.log(
                "Preserving cached paywalls/placements: " +
                    "paywalls=${shouldPreservePaywalls}, placements=${shouldPreservePlacements}"
            )
            newUser.copy(
                paywalls = if (shouldPreservePaywalls) cachedUser.paywalls else newUser.paywalls,
                placements = if (shouldPreservePlacements) cachedUser.placements else newUser.placements
            )
        } else {
            newUser
        }
    }
}
