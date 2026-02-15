package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCredentialsUseCaseTest {

    private val userRepository: UserRepository = mockk {
        every { setUserId(any()) } returns Unit
        every { setDeviceId(any()) } returns Unit
    }
    private val useCase = ResolveCredentialsUseCase(userRepository)

    // region input provided

    @Test
    fun `GIVEN input userId EXPECT saves input userId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = "input-user", inputDeviceId = "input-device")

        verify { userRepository.setUserId("input-user") }
    }

    @Test
    fun `GIVEN input deviceId EXPECT saves input deviceId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = "input-user", inputDeviceId = "input-device")

        verify { userRepository.setDeviceId("input-device") }
    }

    @Test
    fun `GIVEN input differs from cache EXPECT credentialsChanged is true`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        val result = useCase(inputUserId = "input-user", inputDeviceId = "input-device")

        assertTrue(result.credentialsChanged)
    }

    // endregion

    // region input null -- fallback to cache

    @Test
    fun `GIVEN null input and cache exists EXPECT does not write userId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = null, inputDeviceId = null)

        verify(exactly = 0) { userRepository.setUserId(any()) }
    }

    @Test
    fun `GIVEN null input and cache exists EXPECT does not write deviceId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = null, inputDeviceId = null)

        verify(exactly = 0) { userRepository.setDeviceId(any()) }
    }

    @Test
    fun `GIVEN null input and cache exists EXPECT credentialsChanged is false`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        val result = useCase(inputUserId = null, inputDeviceId = null)

        assertFalse(result.credentialsChanged)
    }

    // endregion

    // region input blank -- treated as null

    @Test
    fun `GIVEN blank input and cache exists EXPECT does not write userId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = "  ", inputDeviceId = "")

        verify(exactly = 0) { userRepository.setUserId(any()) }
    }

    @Test
    fun `GIVEN blank input and cache exists EXPECT does not write deviceId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = "  ", inputDeviceId = "")

        verify(exactly = 0) { userRepository.setDeviceId(any()) }
    }

    @Test
    fun `GIVEN blank input and cache exists EXPECT credentialsChanged is false`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        val result = useCase(inputUserId = "  ", inputDeviceId = "")

        assertFalse(result.credentialsChanged)
    }

    // endregion

    // region no input, no cache -- UUID generation

    @Test
    fun `GIVEN no input and no cache EXPECT generates non-blank userId`() {
        every { userRepository.getUserId() } returns null
        every { userRepository.getDeviceId() } returns null

        useCase(inputUserId = null, inputDeviceId = null)

        verify { userRepository.setUserId(match { it.isNotBlank() }) }
    }

    @Test
    fun `GIVEN no input and no cache EXPECT generates non-blank deviceId`() {
        every { userRepository.getUserId() } returns null
        every { userRepository.getDeviceId() } returns null

        useCase(inputUserId = null, inputDeviceId = null)

        verify { userRepository.setDeviceId(match { it.isNotBlank() }) }
    }

    @Test
    fun `GIVEN no input and no cache EXPECT credentialsChanged is true`() {
        every { userRepository.getUserId() } returns null
        every { userRepository.getDeviceId() } returns null

        val result = useCase(inputUserId = null, inputDeviceId = null)

        assertTrue(result.credentialsChanged)
    }

    @Test
    fun `GIVEN no input and no cache EXPECT userId and deviceId share the same UUID`() {
        every { userRepository.getUserId() } returns null
        every { userRepository.getDeviceId() } returns null

        val capturedUserId = mutableListOf<String>()
        val capturedDeviceId = mutableListOf<String>()
        every { userRepository.setUserId(capture(capturedUserId)) } returns Unit
        every { userRepository.setDeviceId(capture(capturedDeviceId)) } returns Unit

        useCase(inputUserId = null, inputDeviceId = null)

        assertEquals(capturedUserId.single(), capturedDeviceId.single())
    }

    // endregion

    // region credentialsChanged detection

    @Test
    fun `GIVEN input matches cache EXPECT credentialsChanged is false`() {
        every { userRepository.getUserId() } returns "same-user"
        every { userRepository.getDeviceId() } returns "same-device"

        val result = useCase(inputUserId = "same-user", inputDeviceId = "same-device")

        assertFalse(result.credentialsChanged)
    }

    @Test
    fun `GIVEN input matches cache EXPECT does not call setUserId`() {
        every { userRepository.getUserId() } returns "same-user"
        every { userRepository.getDeviceId() } returns "same-device"

        useCase(inputUserId = "same-user", inputDeviceId = "same-device")

        verify(exactly = 0) { userRepository.setUserId(any()) }
    }

    @Test
    fun `GIVEN input matches cache EXPECT does not call setDeviceId`() {
        every { userRepository.getUserId() } returns "same-user"
        every { userRepository.getDeviceId() } returns "same-device"

        useCase(inputUserId = "same-user", inputDeviceId = "same-device")

        verify(exactly = 0) { userRepository.setDeviceId(any()) }
    }

    @Test
    fun `GIVEN only userId differs EXPECT credentialsChanged is true`() {
        every { userRepository.getUserId() } returns "old-user"
        every { userRepository.getDeviceId() } returns "same-device"

        val result = useCase(inputUserId = "new-user", inputDeviceId = "same-device")

        assertTrue(result.credentialsChanged)
    }

    @Test
    fun `GIVEN only deviceId differs EXPECT credentialsChanged is true`() {
        every { userRepository.getUserId() } returns "same-user"
        every { userRepository.getDeviceId() } returns "old-device"

        val result = useCase(inputUserId = "same-user", inputDeviceId = "new-device")

        assertTrue(result.credentialsChanged)
    }

    // endregion

    // region mixed: one input provided, other falls back to cache

    @Test
    fun `GIVEN input userId and null deviceId EXPECT saves input userId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = "input-user", inputDeviceId = null)

        verify { userRepository.setUserId("input-user") }
    }

    @Test
    fun `GIVEN input userId and null deviceId EXPECT saves cached deviceId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = "input-user", inputDeviceId = null)

        verify { userRepository.setDeviceId("cached-device") }
    }

    @Test
    fun `GIVEN input userId and null deviceId EXPECT credentialsChanged is true`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        val result = useCase(inputUserId = "input-user", inputDeviceId = null)

        assertTrue(result.credentialsChanged)
    }

    @Test
    fun `GIVEN null userId and input deviceId EXPECT saves cached userId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = null, inputDeviceId = "input-device")

        verify { userRepository.setUserId("cached-user") }
    }

    @Test
    fun `GIVEN null userId and input deviceId EXPECT saves input deviceId`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        useCase(inputUserId = null, inputDeviceId = "input-device")

        verify { userRepository.setDeviceId("input-device") }
    }

    @Test
    fun `GIVEN null userId and input deviceId EXPECT credentialsChanged is true`() {
        every { userRepository.getUserId() } returns "cached-user"
        every { userRepository.getDeviceId() } returns "cached-device"

        val result = useCase(inputUserId = null, inputDeviceId = "input-device")

        assertTrue(result.credentialsChanged)
    }

    // endregion
}
