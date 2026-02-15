package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.ResolveCredentialsResult
import java.util.UUID

internal class ResolveCredentialsUseCase(
    private val userRepository: UserRepository,
) {
    operator fun invoke(inputUserId: String?, inputDeviceId: String?): ResolveCredentialsResult {
        val oldUserId = userRepository.getUserId()
        val oldDeviceId = userRepository.getDeviceId()

        val generatedUUID = UUID.randomUUID().toString()

        val newUserId = if (inputUserId.isNullOrBlank()) {
            oldUserId ?: generatedUUID
        } else {
            inputUserId
        }

        val newDeviceId = if (inputDeviceId.isNullOrBlank()) {
            oldDeviceId ?: generatedUUID
        } else {
            inputDeviceId
        }

        val credentialsChanged = oldUserId != newUserId || oldDeviceId != newDeviceId

        if (credentialsChanged) {
            userRepository.setUserId(newUserId)
            userRepository.setDeviceId(newDeviceId)
        }

        return ResolveCredentialsResult(credentialsChanged = credentialsChanged)
    }
}
