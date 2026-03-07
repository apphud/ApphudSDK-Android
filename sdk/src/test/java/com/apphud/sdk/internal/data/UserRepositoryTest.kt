package com.apphud.sdk.internal.data

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

    private fun createTestPlacement(id: String = "placement-1", paywall: com.apphud.sdk.domain.ApphudPaywall? = null) = ApphudPlacement(
        identifier = "test_placement",
        paywall = paywall,
        id = id
    )

    private fun createTestUser(
        userId: String,
        placements: List<ApphudPlacement> = emptyList(),
        isTemporary: Boolean = false
    ) = ApphudUser(
        userId = userId,
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
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
    fun `setCurrentUser should preserve placements when new user has empty placements`() {
        val placement = createTestPlacement()
        val userWithPlacements = createTestUser(
            userId = "user-1",
            placements = listOf(placement)
        )
        val userWithEmptyPlacements = createTestUser(
            userId = "user-1",
            placements = emptyList()
        )

        repository.setCurrentUser(userWithPlacements)
        repository.setCurrentUser(userWithEmptyPlacements)
        val result = repository.getCurrentUser()

        assertEquals("Placements should be preserved", 1, result?.placements?.size)
        assertEquals("Placement id should match", placement.id, result?.placements?.first()?.id)
    }

    @Test
    fun `setCurrentUser should replace placements when new user has non-empty placements`() {
        val oldPlacement = createTestPlacement(id = "old-placement")
        val newPlacement = createTestPlacement(id = "new-placement")
        val userWithOldPlacements = createTestUser(
            userId = "user-1",
            placements = listOf(oldPlacement)
        )
        val userWithNewPlacements = createTestUser(
            userId = "user-1",
            placements = listOf(newPlacement)
        )

        repository.setCurrentUser(userWithOldPlacements)
        repository.setCurrentUser(userWithNewPlacements)
        val result = repository.getCurrentUser()

        assertEquals("Should have 1 placement", 1, result?.placements?.size)
        assertEquals("Placement should be replaced with new one", "new-placement", result?.placements?.first()?.id)
    }

    @Test
    fun `setCurrentUser should not preserve placements when current user has none`() {
        val userWithEmptyPlacements1 = createTestUser(
            userId = "user-1",
            placements = emptyList()
        )
        val userWithEmptyPlacements2 = createTestUser(
            userId = "user-1",
            placements = emptyList()
        )

        repository.setCurrentUser(userWithEmptyPlacements1)
        repository.setCurrentUser(userWithEmptyPlacements2)
        val result = repository.getCurrentUser()

        assertTrue("Placements should remain empty", result?.placements?.isEmpty() == true)
    }

    @Test
    fun `setCurrentUser should save merged user to dataSource`() {
        val placement = createTestPlacement()
        val userWithPlacements = createTestUser(
            userId = "user-1",
            placements = listOf(placement)
        )
        val userWithEmptyPlacements = createTestUser(
            userId = "user-1",
            placements = emptyList()
        )

        repository.setCurrentUser(userWithPlacements)
        repository.setCurrentUser(userWithEmptyPlacements)

        verify(exactly = 2) { dataSource.saveUser(match { it.placements.size == 1 }) }
    }

    // region getUserId fallback chain

    @Test
    fun `GIVEN no state EXPECT getUserId returns null`() {
        every { dataSource.getCachedUser() } returns null

        val result = repository.getUserId()

        assertNull("Should return null when no state", result)
    }

    @Test
    fun `GIVEN only cachedUser in dataSource EXPECT getUserId returns cachedUser userId`() {
        every { dataSource.getCachedUser() } returns mockUser

        val result = repository.getUserId()

        assertEquals("Should fallback to cachedUser.userId", "user-id-1", result)
    }

    @Test
    fun `GIVEN pendingUserId set EXPECT getUserId returns pendingUserId`() {
        every { dataSource.getCachedUser() } returns null

        repository.setUserId("pending-id")
        val result = repository.getUserId()

        assertEquals("Should return pendingUserId", "pending-id", result)
    }

    @Test
    fun `GIVEN pendingUserId and cachedUser EXPECT getUserId returns pendingUserId`() {
        every { dataSource.getCachedUser() } returns mockUser

        repository.setUserId("pending-id")
        val result = repository.getUserId()

        assertEquals("pendingUserId should take priority over cachedUser", "pending-id", result)
    }

    @Test
    fun `GIVEN currentUser set EXPECT getUserId returns currentUser userId`() {
        repository.setCurrentUser(mockUser)

        val result = repository.getUserId()

        assertEquals("Should return currentUser.userId", "user-id-1", result)
    }

    @Test
    fun `GIVEN currentUser and pendingUserId EXPECT getUserId returns currentUser userId`() {
        repository.setUserId("pending-id")
        repository.setCurrentUser(mockUser)

        val result = repository.getUserId()

        assertEquals("currentUser should take priority over pendingUserId", "user-id-1", result)
    }

    // endregion

    // region setCurrentUser clears pendingUserId

    @Test
    fun `GIVEN pendingUserId then setCurrentUser EXPECT pendingUserId cleared`() {
        repository.setUserId("pending-id")
        repository.setCurrentUser(mockUser)

        val result = repository.getUserId()

        assertEquals("pendingUserId should be cleared, returning currentUser.userId", "user-id-1", result)
    }

    // endregion

    // region clearUser (logout)

    @Test
    fun `GIVEN currentUser set then clearUser EXPECT getUserId returns null`() {
        every { dataSource.getCachedUser() } returns null
        repository.setCurrentUser(mockUser)

        repository.clearUser()
        val result = repository.getUserId()

        assertNull("getUserId should return null after clearUser", result)
    }

    @Test
    fun `GIVEN pendingUserId set then clearUser EXPECT getUserId returns null`() {
        every { dataSource.getCachedUser() } returns null
        repository.setUserId("pending-id")

        repository.clearUser()
        val result = repository.getUserId()

        assertNull("getUserId should return null after clearUser", result)
    }

    @Test
    fun `GIVEN currentUser and pendingUserId then clearUser EXPECT getCurrentUser returns null`() {
        every { dataSource.getCachedUser() } returns null
        repository.setUserId("pending-id")
        repository.setCurrentUser(mockUser)

        repository.clearUser()
        val result = repository.getCurrentUser()

        assertNull("getCurrentUser should return null after clearUser", result)
    }

    @Test
    fun `GIVEN clearUser called EXPECT dataSource clearUser called`() {
        repository.setUserId("pending-id")
        repository.setCurrentUser(mockUser)

        repository.clearUser()

        verify { dataSource.clearUser() }
    }

    @Test
    fun `GIVEN clearUser then setUserId EXPECT getUserId returns new pendingUserId`() {
        every { dataSource.getCachedUser() } returns null
        repository.setCurrentUser(mockUser)
        repository.clearUser()

        repository.setUserId("new-pending-id")
        val result = repository.getUserId()

        assertEquals("Should return new pendingUserId after clearUser", "new-pending-id", result)
    }

    // endregion
}
