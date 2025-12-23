package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserDataSourceTest {

    private lateinit var storage: SharedPreferencesStorage
    private lateinit var dataSource: UserDataSource
    private val mockUser: ApphudUser = mockk(relaxed = true)

    @Before
    fun setup() {
        storage = mockk(relaxed = true)
        dataSource = UserDataSource(storage)
    }

    @Test
    fun `getCachedUser should return user from storage`() {
        every { storage.apphudUser } returns mockUser

        val result = dataSource.getCachedUser()

        assertEquals("Should return user from storage", mockUser, result)
    }

    @Test
    fun `getCachedUser should return null when no user in storage`() {
        every { storage.apphudUser } returns null

        val result = dataSource.getCachedUser()

        assertNull("Should return null when no user cached", result)
    }

    @Test
    fun `saveUser should call storage updateCustomer and return result`() {
        every { storage.updateUser(mockUser) } returns true

        val result = dataSource.saveUser(mockUser)

        assertTrue("Should return true when userId changed", result)
        verify { storage.updateUser(mockUser) }
    }

    @Test
    fun `saveUser should return false when userId did not change`() {
        every { storage.updateUser(mockUser) } returns false

        val result = dataSource.saveUser(mockUser)

        assertFalse("Should return false when userId unchanged", result)
        verify { storage.updateUser(mockUser) }
    }

    @Test
    fun `clearUser should clear both apphudUser and userId in storage`() {
        dataSource.clearUser()

        verify { storage.apphudUser = null }
        verify { storage.userId = null }
    }

    @Test
    fun `getUserId should return userId from storage`() {
        val expectedUserId = "test-user-id"
        every { storage.userId } returns expectedUserId

        val result = dataSource.getUserId()

        assertEquals("Should return userId from storage", expectedUserId, result)
    }

    @Test
    fun `getUserId should return null when no userId in storage`() {
        every { storage.userId } returns null

        val result = dataSource.getUserId()

        assertNull("Should return null when no userId in storage", result)
    }

    @Test
    fun `saveUserId should save userId to storage`() {
        val userId = "test-user-id"

        dataSource.saveUserId(userId)

        verify { storage.userId = userId }
    }

    @Test
    fun `getDeviceId should return deviceId from storage`() {
        val expectedDeviceId = "test-device-id"
        every { storage.deviceId } returns expectedDeviceId

        val result = dataSource.getDeviceId()

        assertEquals("Should return deviceId from storage", expectedDeviceId, result)
    }

    @Test
    fun `getDeviceId should return null when no deviceId in storage`() {
        every { storage.deviceId } returns null

        val result = dataSource.getDeviceId()

        assertNull("Should return null when no deviceId in storage", result)
    }

    @Test
    fun `saveDeviceId should save deviceId to storage`() {
        val deviceId = "test-device-id"

        dataSource.saveDeviceId(deviceId)

        verify { storage.deviceId = deviceId }
    }

    @Test
    fun `isCacheExpired should return true when cache expired`() {
        every { storage.cacheExpired() } returns true

        val result = dataSource.isCacheExpired()

        assertTrue("Should return true when cache expired", result)
    }

    @Test
    fun `isCacheExpired should return false when cache not expired`() {
        every { storage.cacheExpired() } returns false

        val result = dataSource.isCacheExpired()

        assertFalse("Should return false when cache not expired", result)
    }

    @Test
    fun `updateLastRegistrationTime should update timestamp in storage`() {
        val timestamp = 1234567890L

        dataSource.updateLastRegistrationTime(timestamp)

        verify { storage.lastRegistration = timestamp }
    }
}
