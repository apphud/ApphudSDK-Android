package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetPaywallByIdentifierUseCaseTest {

    private val userRepository: UserRepository = mockk()
    private val remoteRepository: RemoteRepository = mockk()

    private val useCase = GetPaywallByIdentifierUseCase(userRepository, remoteRepository)

    private fun paywall(identifier: String, id: String = "pw-$identifier") = ApphudPaywall(
        id = id,
        name = "Test",
        identifier = identifier,
        default = false,
        json = null,
        products = null,
        screen = null,
        experimentName = null,
        variationName = null,
        variationIdentifier = null,
        parentPaywallIdentifier = null,
        placementIdentifier = null,
        placementId = null,
    )

    private fun userWithPaywall(paywall: ApphudPaywall): ApphudUser {
        val placement = ApphudPlacement.createCustom("placement", paywall)
        return mockk { every { placements } returns listOf(placement) }
    }

    // region cache hit

    @Test
    fun `GIVEN cached paywall with identifier EXPECT returns cached paywall`() = runTest {
        val cached = paywall("main")
        every { userRepository.getCurrentUser() } returns userWithPaywall(cached)

        val result = useCase("main", "device-id")

        assertEquals(cached, result)
    }

    @Test
    fun `GIVEN cached paywall with identifier EXPECT does not call remote`() = runTest {
        val cached = paywall("main")
        every { userRepository.getCurrentUser() } returns userWithPaywall(cached)

        useCase("main", "device-id")

        coVerify(exactly = 0) { remoteRepository.getPaywall(any(), any()) }
    }

    @Test
    fun `GIVEN cached paywall with id EXPECT returns cached paywall`() = runTest {
        val cached = paywall("main", id = "pw-internal-id")
        every { userRepository.getCurrentUser() } returns userWithPaywall(cached)

        val result = useCase("pw-internal-id", "device-id")

        assertEquals(cached, result)
    }

    @Test
    fun `GIVEN cached paywall with id EXPECT does not call remote`() = runTest {
        val cached = paywall("main", id = "pw-internal-id")
        every { userRepository.getCurrentUser() } returns userWithPaywall(cached)

        useCase("pw-internal-id", "device-id")

        coVerify(exactly = 0) { remoteRepository.getPaywall(any(), any()) }
    }

    // endregion

    // region cache miss -- remote fallback

    @Test
    fun `GIVEN no cached paywall EXPECT returns remote paywall`() = runTest {
        val remote = paywall("main")
        every { userRepository.getCurrentUser() } returns userWithPaywall(paywall("other"))
        coEvery { remoteRepository.getPaywall("main", "device-id") } returns Result.success(remote)

        val result = useCase("main", "device-id")

        assertEquals(remote, result)
    }

    @Test
    fun `GIVEN no current user EXPECT fetches paywall remotely`() = runTest {
        val remote = paywall("main")
        every { userRepository.getCurrentUser() } returns null
        coEvery { remoteRepository.getPaywall("main", "device-id") } returns Result.success(remote)

        val result = useCase("main", "device-id")

        assertEquals(remote, result)
    }

    @Test
    fun `GIVEN cache miss and remote failure EXPECT returns null`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { remoteRepository.getPaywall("main", "device-id") } returns
            Result.failure(RuntimeException("network error"))

        val result = useCase("main", "device-id")

        assertNull(result)
    }

    // endregion
}
