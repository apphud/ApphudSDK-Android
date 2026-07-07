package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.remote.RemoteRepository
import com.apphud.sdk.storage.Storage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitPushTokenUseCaseTest {

    private val remoteRepository: RemoteRepository = mockk()
    private val userRepository: UserRepository = mockk {
        every { getDeviceId() } returns "device-id"
    }
    private val storage: Storage = mockk(relaxUnitFun = true) {
        every { submittedPushToken } returns null
    }

    private val useCase = SubmitPushTokenUseCase(remoteRepository, userRepository, storage)

    @Test
    fun `GIVEN new token EXPECT submits to remote`() = runTest {
        coEvery { remoteRepository.submitPushToken("device-id", "token-1") } returns Result.success(Unit)

        useCase("token-1")

        coVerify { remoteRepository.submitPushToken("device-id", "token-1") }
    }

    @Test
    fun `GIVEN successful submit EXPECT stores token`() = runTest {
        coEvery { remoteRepository.submitPushToken("device-id", "token-1") } returns Result.success(Unit)

        useCase("token-1")

        verify { storage.submittedPushToken = "token-1" }
    }

    @Test
    fun `GIVEN successful submit EXPECT returns true`() = runTest {
        coEvery { remoteRepository.submitPushToken("device-id", "token-1") } returns Result.success(Unit)

        val result = useCase("token-1")

        assertTrue(result)
    }

    @Test
    fun `GIVEN token equal to submitted token EXPECT skips remote`() = runTest {
        every { storage.submittedPushToken } returns "token-1"

        useCase("token-1")

        coVerify(exactly = 0) { remoteRepository.submitPushToken(any(), any()) }
    }

    @Test
    fun `GIVEN empty token EXPECT skips remote`() = runTest {
        useCase("")

        coVerify(exactly = 0) { remoteRepository.submitPushToken(any(), any()) }
    }

    @Test
    fun `GIVEN no device id EXPECT returns false`() = runTest {
        every { userRepository.getDeviceId() } returns null

        val result = useCase("token-1")

        assertFalse(result)
    }

    @Test
    fun `GIVEN remote failure EXPECT returns false`() = runTest {
        coEvery { remoteRepository.submitPushToken("device-id", "token-1") } returns
            Result.failure(RuntimeException("network"))

        val result = useCase("token-1")

        assertFalse(result)
    }

    @Test
    fun `GIVEN remote failure EXPECT does not store token`() = runTest {
        coEvery { remoteRepository.submitPushToken("device-id", "token-1") } returns
            Result.failure(RuntimeException("network"))

        useCase("token-1")

        verify(exactly = 0) { storage.submittedPushToken = "token-1" }
    }
}
