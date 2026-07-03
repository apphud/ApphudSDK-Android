package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository

/**
 * Resolves a paywall by its identifier, mirroring iOS `fetchPaywall(identifier:)`:
 * the cached placements are checked first, and a remote request is made only on a cache miss.
 */
internal class GetPaywallByIdentifierUseCase(
    private val userRepository: UserRepository,
    private val remoteRepository: RemoteRepository,
) {

    suspend operator fun invoke(identifier: String, deviceId: String): ApphudPaywall? {
        cachedPaywall(identifier)?.let { return it }
        return remoteRepository.getPaywall(identifier, deviceId).getOrNull()
    }

    private fun cachedPaywall(identifier: String): ApphudPaywall? =
        userRepository.getCurrentUser()
            ?.placements
            ?.firstNotNullOfOrNull { placement ->
                placement.paywall?.takeIf { it.identifier == identifier }
            }
}
