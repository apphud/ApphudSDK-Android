package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserDataSource
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.toApphudError

internal class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val userDataSource: UserDataSource,
    private val requestManager: RequestManager,
) {

    suspend operator fun invoke(
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean,
        email: String? = null,
        fromWeb2Web: Boolean = false,
        /** Identity version 1: the user id this registration sends, as SDK 3.6 passed it. */
        userId: String? = null,
        cachedUser: ApphudUser? = null,
    ): ApphudUser {
        val registrationType = if (forceRegistration) "Force Registration" else "Registration"
        ApphudLog.log(
            "$registrationType: needPlacementsPaywalls=$needPlacementsPaywalls, " +
                "isNew=$isNew, userId=$userId, email=$email, fromWeb2Web=$fromWeb2Web",
        )

        val newUser = runCatchingCancellable {
            requestManager.registration(
                needPlacements = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = forceRegistration,
                email = email,
                fromWeb2Web = fromWeb2Web,
                userId = userId,
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

        val shouldPreservePlacements = newUser.placements.isEmpty() && cachedUser.placements.isNotEmpty()

        return if (shouldPreservePlacements) {
            newUser.copy(
                placements = cachedUser.placements,
            )
        } else {
            newUser
        }
    }
}
