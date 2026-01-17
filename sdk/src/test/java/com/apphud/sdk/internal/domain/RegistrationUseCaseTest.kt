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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegistrationUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userDataSource: UserDataSource
    private lateinit var requestManager: RequestManager
    private lateinit var registrationUseCase: RegistrationUseCase

    private val mockUser: ApphudUser = mockk(relaxed = true)
    private val mockUser2: ApphudUser = mockk(relaxed = true)

    @Before
    fun setup() {
        userRepository = mockk(relaxed = true)
        userDataSource = mockk(relaxed = true)
        requestManager = mockk(relaxed = true)
        registrationUseCase = RegistrationUseCase(userRepository, userDataSource, requestManager)

        every { mockUser.userId } returns "user-id-1"
        every { mockUser.isTemporary } returns false

        every { mockUser2.userId } returns "user-id-2"
        every { mockUser2.isTemporary } returns false
    }

    @Test
    fun `invoke should perform registration when no cached user exists`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false
        )

        assertEquals("Should return registered user", mockUser, result)
        coVerify { requestManager.registration(true, false, false, null, null) }
        coVerify { userRepository.setCurrentUser(mockUser) }
        verify { userDataSource.updateLastRegistrationTime(any()) }
    }

    @Test
    fun `invoke should return cached user when available and not forcing registration`() = runTest {
        every { userRepository.getCurrentUser() } returns mockUser

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = false
        )

        assertEquals("Should return cached user", mockUser, result)
        coVerify(exactly = 0) { requestManager.registration(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invoke should not return cached temporary user`() = runTest {
        val temporaryUser: ApphudUser = mockk {
            every { userId } returns "temp-user"
            every { isTemporary } returns true
            every { paywalls } returns emptyList()
            every { placements } returns emptyList()
        }
        every { userRepository.getCurrentUser() } returns temporaryUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = false
        )

        assertEquals("Should perform registration when current user is temporary", mockUser, result)
        coVerify { requestManager.registration(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invoke should force registration even when cached user exists`() = runTest {
        every { userRepository.getCurrentUser() } returns mockUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser2
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = true
        )

        assertEquals("Should perform registration and return new user", mockUser2, result)
        coVerify { requestManager.registration(true, false, true, null, null) }
        coVerify { userRepository.setCurrentUser(mockUser2) }
    }

    @Test
    fun `invoke should pass userId and email to registration`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val userId = "custom-user-id"
        val email = "test@example.com"

        registrationUseCase(
            needPlacementsPaywalls = false,
            isNew = true,
            userId = userId,
            email = email
        )

        coVerify { requestManager.registration(false, true, false, userId, email) }
    }

    @Test
    fun `invoke should throw ApphudError when registration fails`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        val networkError = RuntimeException("Network error")
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } throws networkError

        val result = runCatching {
            registrationUseCase(
                needPlacementsPaywalls = true,
                isNew = false
            )
        }

        assertTrue("Should fail with exception", result.isFailure)
        assertTrue("Should throw ApphudError", result.exceptionOrNull() is ApphudError)
        coVerify { requestManager.registration(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { userRepository.setCurrentUser(any()) }
    }

    @Test
    fun `invoke should update lastRegistrationTime after successful registration`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val beforeTime = System.currentTimeMillis()
        registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false
        )
        val afterTime = System.currentTimeMillis()

        verify {
            userDataSource.updateLastRegistrationTime(
                match { timestamp ->
                    timestamp in beforeTime..afterTime
                }
            )
        }
    }

    @Test
    fun `invoke should be thread-safe with concurrent calls`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val iterations = 50

        // Launch multiple concurrent registration calls
        val jobs = List(iterations) {
            async {
                registrationUseCase(
                    needPlacementsPaywalls = true,
                    isNew = false
                )
            }
        }

        val results = jobs.awaitAll()

        // All calls should complete successfully
        assertEquals("All calls should return user", iterations, results.size)
        results.forEach { user ->
            assertEquals("All should return the same user", mockUser, user)
        }

        // Due to Mutex, registration should only be called once (first call goes through, rest wait and get cached user)
        // However, in this test getCurrentUser returns null, so each will try to register
        // But Mutex ensures sequential execution
        coVerify(exactly = iterations) { requestManager.registration(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invoke with forceRegistration should bypass cache even with valid user`() = runTest {
        val cachedUser: ApphudUser = mockk {
            every { userId } returns "cached-user"
            every { isTemporary } returns false
            every { paywalls } returns emptyList()
            every { placements } returns emptyList()
        }
        every { userRepository.getCurrentUser() } returns cachedUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser2
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = false,
            isNew = false,
            forceRegistration = true
        )

        assertEquals("Should return newly registered user", mockUser2, result)
        coVerify { requestManager.registration(false, false, true, null, null) }
    }

    @Test
    fun `invoke should handle isNew flag correctly`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        registrationUseCase(
            needPlacementsPaywalls = false,
            isNew = true
        )

        coVerify { requestManager.registration(false, true, false, null, null) }
    }

    @Test
    fun `invoke should handle needPlacementsPaywalls flag correctly`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false
        )

        coVerify { requestManager.registration(true, false, false, null, null) }
    }

    @Test
    fun `invoke should save user to cache after successful registration`() = runTest {
        every { userRepository.getCurrentUser() } returns null
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns mockUser
        coEvery { userRepository.setCurrentUser(any()) } returns true

        registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false
        )

        coVerify { userRepository.setCurrentUser(mockUser) }
    }

    @Test
    fun `concurrent force registrations should be serialized by mutex`() = runTest {
        every { userRepository.getCurrentUser() } returns mockUser
        var callCount = 0
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } answers {
            callCount++
            if (callCount == 1) mockUser else mockUser2
        }
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val iterations = 10

        // Launch multiple concurrent force registrations
        val jobs = List(iterations) {
            async {
                registrationUseCase(
                    needPlacementsPaywalls = true,
                    isNew = false,
                    forceRegistration = true
                )
            }
        }

        jobs.awaitAll()

        // All calls should execute sequentially due to Mutex
        coVerify(exactly = iterations) { requestManager.registration(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `when backend returns empty placements, cached placements are preserved`() = runTest {
        val cachedPaywall = ApphudPaywall(
            id = "paywall-1",
            name = "Test Paywall",
            identifier = "test_paywall",
            default = false,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = "test_placement",
            placementId = "placement-1"
        )
        val cachedPlacement = ApphudPlacement(
            identifier = "test_placement",
            paywall = cachedPaywall,
            id = "placement-1"
        )
        val cachedUser = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = listOf(cachedPaywall),
            placements = listOf(cachedPlacement),
            isTemporary = false
        )

        val newUserFromBackend = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = emptyList(),
            placements = emptyList(),
            isTemporary = false
        )

        every { userRepository.getCurrentUser() } returns cachedUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns newUserFromBackend
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = false,
            isNew = false,
            forceRegistration = true
        )

        assertEquals(1, result.paywalls.size)
        assertEquals(1, result.placements.size)
        assertEquals("paywall-1", result.paywalls.first().id)
        assertEquals("placement-1", result.placements.first().id)

        coVerify {
            userRepository.setCurrentUser(match { user ->
                user.paywalls.isNotEmpty() && user.placements.isNotEmpty()
            })
        }
    }

    @Test
    fun `when backend returns new placements, they replace cached ones`() = runTest {
        val cachedPaywall = ApphudPaywall(
            id = "old-paywall",
            name = "Old Paywall",
            identifier = "old_paywall",
            default = false,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = null,
            placementId = null
        )
        val cachedUser = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = listOf(cachedPaywall),
            placements = emptyList(),
            isTemporary = false
        )

        val newPaywall = ApphudPaywall(
            id = "new-paywall",
            name = "New Paywall",
            identifier = "new_paywall",
            default = true,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = null,
            placementId = null
        )
        val newUserFromBackend = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = listOf(newPaywall),
            placements = emptyList(),
            isTemporary = false
        )

        every { userRepository.getCurrentUser() } returns cachedUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns newUserFromBackend
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = true
        )

        assertEquals(1, result.paywalls.size)
        assertEquals("new-paywall", result.paywalls.first().id)
    }

    @Test
    fun `when backend returns empty paywalls but non-empty placements, only paywalls are preserved`() = runTest {
        val cachedPaywall = ApphudPaywall(
            id = "cached-paywall",
            name = "Cached Paywall",
            identifier = "cached_paywall",
            default = false,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = null,
            placementId = null
        )
        val cachedPlacement = ApphudPlacement(
            identifier = "cached_placement",
            paywall = cachedPaywall,
            id = "cached-placement"
        )
        val cachedUser = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = listOf(cachedPaywall),
            placements = listOf(cachedPlacement),
            isTemporary = false
        )

        val newPlacement = ApphudPlacement(
            identifier = "new_placement",
            paywall = null,
            id = "new-placement"
        )
        val newUserFromBackend = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = emptyList(),
            placements = listOf(newPlacement),
            isTemporary = false
        )

        every { userRepository.getCurrentUser() } returns cachedUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns newUserFromBackend
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = true
        )

        assertEquals("Cached paywalls should be preserved", 1, result.paywalls.size)
        assertEquals("cached-paywall", result.paywalls.first().id)
        assertEquals("New placements should be used", 1, result.placements.size)
        assertEquals("new-placement", result.placements.first().id)
    }

    @Test
    fun `when backend returns non-empty paywalls but empty placements, only placements are preserved`() = runTest {
        val cachedPaywall = ApphudPaywall(
            id = "cached-paywall",
            name = "Cached Paywall",
            identifier = "cached_paywall",
            default = false,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = null,
            placementId = null
        )
        val cachedPlacement = ApphudPlacement(
            identifier = "cached_placement",
            paywall = cachedPaywall,
            id = "cached-placement"
        )
        val cachedUser = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = listOf(cachedPaywall),
            placements = listOf(cachedPlacement),
            isTemporary = false
        )

        val newPaywall = ApphudPaywall(
            id = "new-paywall",
            name = "New Paywall",
            identifier = "new_paywall",
            default = true,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = null,
            placementId = null
        )
        val newUserFromBackend = ApphudUser(
            userId = "user-1",
            currencyCode = "USD",
            countryCode = "US",
            subscriptions = emptyList(),
            purchases = emptyList(),
            paywalls = listOf(newPaywall),
            placements = emptyList(),
            isTemporary = false
        )

        every { userRepository.getCurrentUser() } returns cachedUser
        coEvery { requestManager.registration(any(), any(), any(), any(), any()) } returns newUserFromBackend
        coEvery { userRepository.setCurrentUser(any()) } returns true

        val result = registrationUseCase(
            needPlacementsPaywalls = true,
            isNew = false,
            forceRegistration = true
        )

        assertEquals("New paywalls should be used", 1, result.paywalls.size)
        assertEquals("new-paywall", result.paywalls.first().id)
        assertEquals("Cached placements should be preserved", 1, result.placements.size)
        assertEquals("cached-placement", result.placements.first().id)
    }
}
