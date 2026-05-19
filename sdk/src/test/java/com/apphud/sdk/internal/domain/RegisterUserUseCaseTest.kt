package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserDataSource
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.managers.RequestManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegisterUserUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userDataSource: UserDataSource
    private lateinit var requestManager: RequestManager
    private lateinit var useCase: RegisterUserUseCase

    private val user = user(id = "user-1")

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        userDataSource = mockk(relaxed = true)
        requestManager = mockk(relaxed = true)
        useCase = RegisterUserUseCase(
            userRepository = userRepository,
            userDataSource = userDataSource,
            requestManager = requestManager,
        )
    }

    @Test
    fun `GIVEN registration succeeds EXPECT request manager called and user saved`() = runTest {
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns user
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val beforeTime = System.currentTimeMillis()
        val result = useCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = false,
            userId = "custom-id",
            email = "test@example.com",
            cachedUser = null,
        )
        val afterTime = System.currentTimeMillis()

        assertEquals(user, result)
        coVerify { requestManager.registration(true, false, false, "custom-id", "test@example.com") }
        coVerify { userRepository.setCurrentUser(user) }
        verify {
            userDataSource.updateLastRegistrationTime(match { it in beforeTime..afterTime })
        }
    }

    @Test
    fun `GIVEN backend returns empty placements EXPECT cached placements preserved`() = runTest {
        val cachedPlacement = placement(id = "cached-placement")
        val cachedUser = user(id = "user-1", placements = listOf(cachedPlacement))
        val backendUser = user(id = "user-1", placements = emptyList())
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns backendUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = useCase(
            needPlacementsPaywalls = false,
            isNew = false,
            forceRegistration = true,
            cachedUser = cachedUser,
        )

        assertEquals(listOf(cachedPlacement), result.placements)
        coVerify { userRepository.setCurrentUser(match { it.placements == listOf(cachedPlacement) }) }
    }

    @Test
    fun `GIVEN backend returns placements EXPECT backend placements replace cached`() = runTest {
        val cachedUser = user(id = "user-1", placements = listOf(placement(id = "cached-placement")))
        val backendPlacement = placement(id = "backend-placement")
        val backendUser = user(id = "user-1", placements = listOf(backendPlacement))
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns backendUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = useCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = true,
            cachedUser = cachedUser,
        )

        assertEquals(listOf(backendPlacement), result.placements)
    }

    @Test
    fun `GIVEN registration fails EXPECT ApphudError and user is not saved`() = runTest {
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = runCatching {
            useCase(
                needPlacementsPaywalls = true,
                isNew = false,
                forceRegistration = false,
                cachedUser = null,
            )
        }

        assertTrue(result.exceptionOrNull() is ApphudError)
        coVerify(exactly = 0) { userRepository.setCurrentUser(any()) }
    }

    private fun user(
        id: String,
        placements: List<ApphudPlacement> = emptyList(),
    ): ApphudUser =
        ApphudUser(
            userId = id,
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            placements = placements,
            isTemporary = false,
        )

    private fun placement(id: String): ApphudPlacement =
        ApphudPlacement(
            identifier = id,
            paywall = ApphudPaywall(
                id = "paywall-$id",
                name = "Paywall",
                identifier = "paywall_$id",
                default = false,
                json = null,
                products = null,
                screen = null,
                experimentName = null,
                variationName = null,
                variationIdentifier = null,
                parentPaywallIdentifier = null,
                placementIdentifier = id,
                placementId = id,
            ),
            id = id,
        )
}
