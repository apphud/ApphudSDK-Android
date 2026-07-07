package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.storage.Storage

/**
 * Submits the device push token to Apphud, deduplicating repeated submissions of the same token.
 *
 * Mirrors iOS `ApphudInternal.submitPushNotificationsTokenString(_:callback:)` which skips the
 * request when the token matches the previously submitted one.
 */
internal class SubmitPushTokenUseCase(
    private val remoteRepository: RemoteRepository,
    private val userRepository: UserRepository,
    private val storage: Storage,
) {

    suspend operator fun invoke(token: String): Boolean {
        if (token.isEmpty() || storage.submittedPushToken == token) {
            ApphudLog.log("Already submitted the same push token, exiting")
            return true
        }

        val deviceId = userRepository.getDeviceId()
        if (deviceId == null) {
            ApphudLog.logE("Cannot submit push token: SDK not initialized")
            return false
        }

        return runCatchingCancellable {
            remoteRepository.submitPushToken(deviceId, token).getOrThrow()
            storage.submittedPushToken = token
            true
        }.getOrElse { error ->
            ApphudLog.logE("Failed to submit push token: ${error.message}")
            false
        }
    }
}
