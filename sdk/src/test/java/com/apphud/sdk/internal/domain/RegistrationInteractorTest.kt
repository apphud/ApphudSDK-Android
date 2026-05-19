package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserRepository
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

class RegistrationInteractorTest {

    private lateinit var userRepository: UserRepository
    private lateinit var registerUserUseCase: RegisterUserUseCase
    private lateinit var enrichPlacementProductsUseCase: EnrichPlacementProductsUseCase
    private lateinit var interactor: RegistrationInteractor

    private val cachedUser = user(id = "cached-user")
    private val finalUser = user(id = "final-user")

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        registerUserUseCase = mockk(relaxed = true)
        enrichPlacementProductsUseCase = mockk(relaxed = true)
        interactor = RegistrationInteractor(
            userRepository = userRepository,
            registerUserUseCase = registerUserUseCase,
            enrichPlacementProductsUseCase = enrichPlacementProductsUseCase,
        )
    }

    @Test
    fun `GIVEN cached valid user EXPECT no registration and enrichment with cached user`() = runTest {
        every { userRepository.getCurrentUser() } returns cachedUser

        val result = interactor(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = false,
        )

        assertEquals(cachedUser, result)
        coVerify(exactly = 0) { registerUserUseCase(any(), any(), any(), any(), any(), any()) }
        verify { enrichPlacementProductsUseCase(cachedUser) }
    }

    @Test
    fun `GIVEN temporary user EXPECT registration with cached temporary user`() = runTest {
        val temporaryUser = user(id = "temporary-user", isTemporary = true)
        every { userRepository.getCurrentUser() } returns temporaryUser
        coEvery { registerUserUseCase(any(), any(), any(), any(), any(), any()) } returns finalUser

        interactor(needPlacementsPaywalls = true, isNew = false)

        coVerify {
            registerUserUseCase(
                needPlacementsPaywalls = true,
                isNew = false,
                forceRegistration = false,
                userId = null,
                email = null,
                cachedUser = temporaryUser,
            )
        }
    }

    @Test
    fun `GIVEN force registration EXPECT register use case called`() = runTest {
        every { userRepository.getCurrentUser() } returns cachedUser
        coEvery { registerUserUseCase(any(), any(), any(), any(), any(), any()) } returns finalUser

        interactor(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = true,
            userId = "custom-id",
            email = "test@example.com",
        )

        coVerify {
            registerUserUseCase(
                needPlacementsPaywalls = true,
                isNew = false,
                forceRegistration = true,
                userId = "custom-id",
                email = "test@example.com",
                cachedUser = cachedUser,
            )
        }
    }

    @Test
    fun `GIVEN no cached user EXPECT register use case called with null cached user`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { registerUserUseCase(any(), any(), any(), any(), any(), any()) } returns finalUser

        interactor(needPlacementsPaywalls = false, isNew = true)

        coVerify {
            registerUserUseCase(
                needPlacementsPaywalls = false,
                isNew = true,
                forceRegistration = false,
                userId = null,
                email = null,
                cachedUser = null,
            )
        }
    }

    @Test
    fun `GIVEN successful registration EXPECT enrichment with final user`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { registerUserUseCase(any(), any(), any(), any(), any(), any()) } returns finalUser

        val result = interactor(needPlacementsPaywalls = true, isNew = false)

        assertEquals(finalUser, result)
        verify { enrichPlacementProductsUseCase(finalUser) }
    }

    @Test
    fun `GIVEN failed registration EXPECT no enrichment`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { registerUserUseCase(any(), any(), any(), any(), any(), any()) } throws RuntimeException("Network error")

        val result = runCatching {
            interactor(needPlacementsPaywalls = true, isNew = false)
        }

        assertTrue(result.isFailure)
        verify(exactly = 0) { enrichPlacementProductsUseCase(any()) }
    }

    private fun user(
        id: String,
        isTemporary: Boolean = false,
    ): ApphudUser =
        ApphudUser(
            userId = id,
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            placements = emptyList(),
            isTemporary = isTemporary,
        )
}
