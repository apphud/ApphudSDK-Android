package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
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

    @Before
    fun setup() {
        dataSource = mockk(relaxed = true)
        repository = UserRepository(dataSource)

        every { mockUser.userId } returns "user-id-1"
        every { mockUser.isTemporary } returns false
        every { mockUser.subscriptions } returns emptyList()
        every { mockUser.purchases } returns emptyList()

        every { mockUser2.userId } returns "user-id-2"
        every { mockUser2.isTemporary } returns false
        every { mockUser2.subscriptions } returns emptyList()
        every { mockUser2.purchases } returns emptyList()

        every { dataSource.saveUser(any()) } returns false
    }

    @Test
    fun `getCurrentUser should return null initially`() {
        val result = repository.getCurrentUser()

        assertNull("Should return null when no user set", result)
    }

    @Test
    fun `getCurrentUser should return user after setCurrentUser`() = runTest {
        repository.setCurrentUser(mockUser)

        val result = repository.getCurrentUser()

        assertEquals("Should return the user that was set", mockUser, result)
    }

    @Test
    fun `setCurrentUser should save to cache by default`() = runTest {
        every { dataSource.saveUser(mockUser) } returns true

        val result = repository.setCurrentUser(mockUser)

        assertTrue("Should return true when userId changed", result)
        verify { dataSource.saveUser(mockUser) }
    }

    @Test
    fun `setCurrentUser should not save to cache when saveToCache is false`() = runTest {
        val result = repository.setCurrentUser(mockUser, saveToCache = false)

        assertFalse("Should return false when not saving to cache", result)
        verify(exactly = 0) { dataSource.saveUser(any()) }
    }

    @Test
    fun `setCurrentUser should return false when userId did not change`() = runTest {
        every { dataSource.saveUser(mockUser) } returns false

        val result = repository.setCurrentUser(mockUser, saveToCache = true)

        assertFalse("Should return false when userId unchanged", result)
        verify { dataSource.saveUser(mockUser) }
    }

    @Test
    fun `setCurrentUser should be thread-safe with concurrent writes`() = runTest {
        val iterations = 100
        every { dataSource.saveUser(any()) } returns true

        // Launch multiple concurrent setCurrentUser calls
        val jobs = List(iterations) { index ->
            async {
                val user = if (index % 2 == 0) mockUser else mockUser2
                repository.setCurrentUser(user)
            }
        }

        jobs.awaitAll()

        // Verify that one of the users is set (should be deterministic due to Mutex)
        val finalUser = repository.getCurrentUser()
        assertTrue(
            "Final user should be one of the two users",
            finalUser == mockUser || finalUser == mockUser2
        )

        // Verify saveUser was called correct number of times
        verify(exactly = iterations) { dataSource.saveUser(any()) }
    }

    @Test
    fun `updateUser should update when current user is null`() = runTest {
        every { dataSource.saveUser(mockUser) } returns true

        val result = repository.updateUser(mockUser)

        assertTrue("Should return true when current user is null", result)
        assertEquals("Should set the user", mockUser, repository.getCurrentUser())
        verify { dataSource.saveUser(mockUser) }
    }

    @Test
    fun `updateUser should update when userId differs`() = runTest {
        every { dataSource.saveUser(any()) } returns true
        repository.setCurrentUser(mockUser)

        val result = repository.updateUser(mockUser2)

        assertTrue("Should return true when userId differs", result)
        assertEquals("Should update to new user", mockUser2, repository.getCurrentUser())
    }

    @Test
    fun `updateUser should not update when userId is the same`() = runTest {
        every { dataSource.saveUser(mockUser) } returns true
        repository.setCurrentUser(mockUser)

        val sameUser: ApphudUser = mockk {
            every { userId } returns "user-id-1"
        }

        val result = repository.updateUser(sameUser)

        assertFalse("Should return false when userId is the same", result)
        assertEquals("Should keep original user", mockUser, repository.getCurrentUser())
        verify(exactly = 1) { dataSource.saveUser(any()) } // Only the initial setCurrentUser
    }

    @Test
    fun `clearUser should clear both repository and dataSource`() = runTest {
        repository.setCurrentUser(mockUser)

        repository.clearUser()

        assertNull("Should clear current user", repository.getCurrentUser())
        verify { dataSource.clearUser() }
    }

    @Test
    fun `loadFromCache should return user from dataSource`() {
        every { dataSource.getCachedUser() } returns mockUser

        val result = repository.loadFromCache()

        assertEquals("Should return user from dataSource", mockUser, result)
    }

    @Test
    fun `loadFromCache should return null when no cached user`() {
        every { dataSource.getCachedUser() } returns null

        val result = repository.loadFromCache()

        assertNull("Should return null when no cached user", result)
    }

    @Test
    fun `isCacheExpired should return true when cache expired`() {
        every { dataSource.isCacheExpired() } returns true

        val result = repository.isCacheExpired()

        assertTrue("Should return true when cache expired", result)
    }

    @Test
    fun `isCacheExpired should return false when cache not expired`() {
        every { dataSource.isCacheExpired() } returns false

        val result = repository.isCacheExpired()

        assertFalse("Should return false when cache not expired", result)
    }

    @Test
    fun `isTemporaryUser should return true when current user is temporary`() = runTest {
        val temporaryUser: ApphudUser = mockk {
            every { userId } returns "temp-user"
            every { isTemporary } returns true
        }
        repository.setCurrentUser(temporaryUser, saveToCache = false)

        val result = repository.isTemporaryUser()

        assertTrue("Should return true when user is temporary", result)
    }

    @Test
    fun `isTemporaryUser should return false when current user is not temporary`() = runTest {
        repository.setCurrentUser(mockUser, saveToCache = false)

        val result = repository.isTemporaryUser()

        assertFalse("Should return false when user is not temporary", result)
    }

    @Test
    fun `isTemporaryUser should return false when no current user`() {
        val result = repository.isTemporaryUser()

        assertFalse("Should return false when no current user", result)
    }

    @Test
    fun `userHasPurchases should return true when user has subscriptions`() = runTest {
        val userWithSubscriptions: ApphudUser = mockk {
            every { userId } returns "user-with-subs"
            every { subscriptions } returns listOf(mockk())
            every { purchases } returns emptyList()
        }
        repository.setCurrentUser(userWithSubscriptions, saveToCache = false)

        val result = repository.userHasPurchases()

        assertTrue("Should return true when user has subscriptions", result)
    }

    @Test
    fun `userHasPurchases should return true when user has purchases`() = runTest {
        val userWithPurchases: ApphudUser = mockk {
            every { userId } returns "user-with-purchases"
            every { subscriptions } returns emptyList()
            every { purchases } returns listOf(mockk())
        }
        repository.setCurrentUser(userWithPurchases, saveToCache = false)

        val result = repository.userHasPurchases()

        assertTrue("Should return true when user has purchases", result)
    }

    @Test
    fun `userHasPurchases should return false when user has no subscriptions or purchases`() = runTest {
        repository.setCurrentUser(mockUser, saveToCache = false)

        val result = repository.userHasPurchases()

        assertFalse("Should return false when user has no purchases", result)
    }

    @Test
    fun `userHasPurchases should return false when no current user`() {
        val result = repository.userHasPurchases()

        assertFalse("Should return false when no current user", result)
    }

    @Test
    fun `concurrent setCurrentUser and getCurrentUser should be thread-safe`() = runTest {
        val iterations = 200
        every { dataSource.saveUser(any()) } returns true

        // Launch concurrent reads and writes
        val writeJobs = List(iterations / 2) { index ->
            async {
                val user = if (index % 2 == 0) mockUser else mockUser2
                repository.setCurrentUser(user)
            }
        }

        val readJobs = List(iterations / 2) {
            async {
                repository.getCurrentUser()
            }
        }

        writeJobs.awaitAll()
        val results = readJobs.awaitAll()

        // All reads should return either null, mockUser, or mockUser2 (no corrupted state)
        results.forEach { user ->
            assertTrue(
                "Read should return valid state",
                user == null || user == mockUser || user == mockUser2
            )
        }
    }
}
