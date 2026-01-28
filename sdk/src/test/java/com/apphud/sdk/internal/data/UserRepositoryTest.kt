package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserRepositoryTest {

    private lateinit var dataSource: UserDataSource
    private lateinit var repository: UserRepository
    private val mockUser: ApphudUser = mockk(relaxed = true)
    private val mockUser2: ApphudUser = mockk(relaxed = true)
    private val temporaryUser: ApphudUser = mockk(relaxed = true)

    private fun createTestPaywall(id: String = "paywall-1") = ApphudPaywall(
        id = id,
        name = "Test Paywall",
        identifier = "test_paywall",
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

    private fun createTestPlacement(id: String = "placement-1", paywall: ApphudPaywall? = null) = ApphudPlacement(
        identifier = "test_placement",
        paywall = paywall,
        id = id
    )

    private fun createTestUser(
        userId: String,
        paywalls: List<ApphudPaywall> = emptyList(),
        placements: List<ApphudPlacement> = emptyList(),
        isTemporary: Boolean = false
    ) = ApphudUser(
        userId = userId,
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
        paywalls = paywalls,
        placements = placements,
        isTemporary = isTemporary
    )

    @Before
    fun setup() {
        dataSource = mockk(relaxed = true)
        repository = UserRepository(dataSource)

        every { mockUser.userId } returns "user-id-1"
        every { mockUser.isTemporary } returns false
        every { mockUser2.userId } returns "user-id-2"
        every { mockUser2.isTemporary } returns false
        every { temporaryUser.userId } returns "temp-user-id"
        every { temporaryUser.isTemporary } returns true
    }

    @Test
    fun `getCurrentUser should return null when no user set and dataSource returns null`() {
        every { dataSource.getCachedUser() } returns null

        val result = repository.getCurrentUser()

        assertNull("Should return null when no user", result)
    }

    @Test
    fun `getCurrentUser should return user from dataSource when no in-memory user`() {
        every { dataSource.getCachedUser() } returns mockUser

        val result = repository.getCurrentUser()

        assertEquals("Should return user from dataSource", mockUser, result)
    }

    @Test
    fun `getCurrentUser should return in-memory user over dataSource`() {
        every { dataSource.getCachedUser() } returns mockUser2

        repository.setCurrentUser(mockUser)
        val result = repository.getCurrentUser()

        assertEquals("Should return in-memory user", mockUser, result)
    }

    @Test
    fun `setCurrentUser should save non-temporary user to dataSource`() {
        val result = repository.setCurrentUser(mockUser)

        assertTrue("Should return true when userId changed from null", result)
        verify { dataSource.saveUser(mockUser) }
    }

    @Test
    fun `setCurrentUser should NOT save temporary user to dataSource`() {
        val result = repository.setCurrentUser(temporaryUser)

        assertTrue("Should return true when userId changed", result)
        verify(exactly = 0) { dataSource.saveUser(any()) }
    }

    @Test
    fun `setCurrentUser should return false when userId unchanged`() {
        repository.setCurrentUser(mockUser)

        val result = repository.setCurrentUser(mockUser)

        assertFalse("Should return false when userId unchanged", result)
    }

    @Test
    fun `setCurrentUser should return true when userId changed`() {
        repository.setCurrentUser(mockUser)

        val result = repository.setCurrentUser(mockUser2)

        assertTrue("Should return true when userId changed", result)
    }

    @Test
    fun `temporary user should be available via getCurrentUser but not persisted`() {
        repository.setCurrentUser(temporaryUser)

        val result = repository.getCurrentUser()

        assertEquals("Should return temporary user from memory", temporaryUser, result)
        verify(exactly = 0) { dataSource.saveUser(any()) }
    }

    @Test
    fun `clearUser should clear both memory and dataSource`() {
        repository.setCurrentUser(mockUser)

        repository.clearUser()

        verify { dataSource.clearUser() }
        every { dataSource.getCachedUser() } returns null
        assertNull("Should return null after clear", repository.getCurrentUser())
    }

    @Test
    fun `setCurrentUser should preserve paywalls when new user has empty paywalls`() {
        val paywall = createTestPaywall()
        val placement = createTestPlacement(paywall = paywall)
        val userWithPaywalls = createTestUser(
            userId = "user-1",
            paywalls = listOf(paywall),
            placements = listOf(placement)
        )
        val userWithEmptyPaywalls = createTestUser(
            userId = "user-1",
            paywalls = emptyList(),
            placements = emptyList()
        )

        repository.setCurrentUser(userWithPaywalls)
        repository.setCurrentUser(userWithEmptyPaywalls)
        val result = repository.getCurrentUser()

        assertEquals("Paywalls should be preserved", 1, result?.paywalls?.size)
        assertEquals("Placements should be preserved", 1, result?.placements?.size)
        assertEquals("Paywall id should match", paywall.id, result?.paywalls?.first()?.id)
    }

    @Test
    fun `setCurrentUser should replace paywalls when new user has non-empty paywalls`() {
        val oldPaywall = createTestPaywall(id = "old-paywall")
        val newPaywall = createTestPaywall(id = "new-paywall")
        val userWithOldPaywalls = createTestUser(
            userId = "user-1",
            paywalls = listOf(oldPaywall),
            placements = emptyList()
        )
        val userWithNewPaywalls = createTestUser(
            userId = "user-1",
            paywalls = listOf(newPaywall),
            placements = emptyList()
        )

        repository.setCurrentUser(userWithOldPaywalls)
        repository.setCurrentUser(userWithNewPaywalls)
        val result = repository.getCurrentUser()

        assertEquals("Should have 1 paywall", 1, result?.paywalls?.size)
        assertEquals("Paywall should be replaced with new one", "new-paywall", result?.paywalls?.first()?.id)
    }

    @Test
    fun `setCurrentUser should not preserve paywalls when current user has none`() {
        val userWithEmptyPaywalls1 = createTestUser(
            userId = "user-1",
            paywalls = emptyList(),
            placements = emptyList()
        )
        val userWithEmptyPaywalls2 = createTestUser(
            userId = "user-1",
            paywalls = emptyList(),
            placements = emptyList()
        )

        repository.setCurrentUser(userWithEmptyPaywalls1)
        repository.setCurrentUser(userWithEmptyPaywalls2)
        val result = repository.getCurrentUser()

        assertTrue("Paywalls should remain empty", result?.paywalls?.isEmpty() == true)
    }

    @Test
    fun `setCurrentUser should save merged user to dataSource`() {
        val paywall = createTestPaywall()
        val placement = createTestPlacement(paywall = paywall)
        val userWithPaywalls = createTestUser(
            userId = "user-1",
            paywalls = listOf(paywall),
            placements = listOf(placement)
        )
        val userWithEmptyPaywalls = createTestUser(
            userId = "user-1",
            paywalls = emptyList(),
            placements = emptyList()
        )

        repository.setCurrentUser(userWithPaywalls)
        repository.setCurrentUser(userWithEmptyPaywalls)

        verify(exactly = 2) { dataSource.saveUser(match { it.paywalls.size == 1 }) }
    }
}
