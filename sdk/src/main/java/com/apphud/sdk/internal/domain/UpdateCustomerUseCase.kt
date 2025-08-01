package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.local.UserRepository

internal class UpdateCustomerUseCase(
    private val userRepository: UserRepository,
) {

    suspend operator fun invoke(apphudUser: ApphudUser): Boolean {
        val previousUser = userRepository.getCurrentUser()
        val userIdChanged = previousUser?.userId != apphudUser.userId

        userRepository.updateUser(apphudUser)

        return userIdChanged
    }
} 