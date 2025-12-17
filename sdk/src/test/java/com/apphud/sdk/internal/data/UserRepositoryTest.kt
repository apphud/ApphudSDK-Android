package com.apphud.sdk.internal.data

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
}
