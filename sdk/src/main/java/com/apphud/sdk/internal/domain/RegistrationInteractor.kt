package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RegistrationInteractor(
    private val userRepository: UserRepository,
    private val registerUserUseCase: RegisterUserUseCase,
    private val enrichPlacementProductsUseCase: EnrichPlacementProductsUseCase,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(
        needPlacementsPaywalls: Boolean,
        isNew: Boolean,
        forceRegistration: Boolean = false,
        userId: String? = null,
        email: String? = null,
    ): ApphudUser =
        mutex.withLock {
            val currentUser = userRepository.getCurrentUser()
            if (!forceRegistration && currentUser != null && currentUser.isTemporary == false) {
                ApphudLog.log("Registration: User already loaded, returning cached user")
                enrichPlacementProductsUseCase(currentUser)
                return@withLock currentUser
            }

            val finalUser = registerUserUseCase(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = forceRegistration,
                userId = userId,
                email = email,
                cachedUser = currentUser,
            )
            enrichPlacementProductsUseCase(finalUser)
            finalUser
        }
}
