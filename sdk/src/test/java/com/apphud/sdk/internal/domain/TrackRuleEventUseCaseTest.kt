package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.data.dto.RuleEventDto
import com.apphud.sdk.internal.data.remote.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackRuleEventUseCaseTest {

    private val remoteRepository: RemoteRepository = mockk {
        coEvery { trackRuleEvent(any()) } returns Result.success(Unit)
    }
    private val userRepository: UserRepository = mockk {
        every { getDeviceId() } returns "device-id"
    }

    private val useCase = TrackRuleEventUseCase(remoteRepository, userRepository)

    @Test
    fun `GIVEN event EXPECT posts dto with device id`() = runTest {
        val captured = slot<RuleEventDto>()
        coEvery { remoteRepository.trackRuleEvent(capture(captured)) } returns Result.success(Unit)

        useCase("rule-1", "screen-1", "\$feedback", mapOf("question" to "q", "answer" to "a"))

        assertEquals("device-id", captured.captured.deviceId)
    }

    @Test
    fun `GIVEN event EXPECT posts dto with rule id`() = runTest {
        val captured = slot<RuleEventDto>()
        coEvery { remoteRepository.trackRuleEvent(capture(captured)) } returns Result.success(Unit)

        useCase("rule-1", "screen-1", "\$feedback")

        assertEquals("rule-1", captured.captured.ruleId)
    }

    @Test
    fun `GIVEN event EXPECT posts dto with name`() = runTest {
        val captured = slot<RuleEventDto>()
        coEvery { remoteRepository.trackRuleEvent(capture(captured)) } returns Result.success(Unit)

        useCase("rule-1", "screen-1", "\$survey_answer")

        assertEquals("\$survey_answer", captured.captured.name)
    }

    @Test
    fun `GIVEN event with properties EXPECT posts dto with properties`() = runTest {
        val captured = slot<RuleEventDto>()
        coEvery { remoteRepository.trackRuleEvent(capture(captured)) } returns Result.success(Unit)

        useCase("rule-1", "screen-1", "\$feedback", mapOf("question" to "q", "answer" to "a"))

        assertEquals(mapOf("question" to "q", "answer" to "a"), captured.captured.properties)
    }

    @Test
    fun `GIVEN no device id EXPECT does not call remote`() = runTest {
        every { userRepository.getDeviceId() } returns null

        useCase("rule-1", "screen-1", "\$feedback")

        coVerify(exactly = 0) { remoteRepository.trackRuleEvent(any()) }
    }
}
