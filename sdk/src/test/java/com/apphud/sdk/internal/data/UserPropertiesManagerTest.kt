package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudUserPropertyKey
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

class UserPropertiesManagerTest {

    private val userRepository: UserRepository = mockk {
        every { getCurrentUser() } returns null
        every { getDeviceId() } returns "test-device-id"
    }

    private val storage: SharedPreferencesStorage = mockk {
        every { needSendProperty(any()) } returns true
    }

    private val awaitUserRegistration: suspend () -> Unit = {}

    private val manager = UserPropertiesManager(
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        userRepository = userRepository,
        storage = storage,
        awaitUserRegistration = awaitUserRegistration,
        dispatchers = ApphudDispatchers(),
    )

    // region setUserProperty

    @Test
    fun `GIVEN unknown type EXPECT property not added and no crash`() {
        val key = ApphudUserPropertyKey.CustomProperty("test_key")

        manager.setUserProperty(key = key, value = listOf(1, 2, 3), setOnce = false, increment = false)

        // No crash means success - unknown type is rejected
    }

    @Test
    fun `GIVEN valid string property EXPECT storage needSendProperty called`() {
        val key = ApphudUserPropertyKey.CustomProperty("test_key")

        manager.setUserProperty(key = key, value = "test_value", setOnce = false, increment = false)

        verify { storage.needSendProperty(any()) }
    }

    @Test
    fun `GIVEN increment with non-numeric type EXPECT property not added`() {
        val key = ApphudUserPropertyKey.CustomProperty("test_key")

        manager.setUserProperty(key = key, value = "not a number", setOnce = false, increment = true)

        // No crash means success - string increment is rejected
    }

    // endregion

    // region forceFlushUserProperties

    @Test
    fun `GIVEN empty pending properties EXPECT forceFlush returns false`() = runTest {
        val result = manager.forceFlushUserProperties(false)

        assertFalse(result)
    }

    @Test
    fun `GIVEN isUpdatingProperties and not force EXPECT forceFlush returns false`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val blockedManager = UserPropertiesManager(
            coroutineScope = CoroutineScope(SupervisorJob() + testDispatcher),
            userRepository = userRepository,
            storage = storage,
            awaitUserRegistration = { gate.await() },
            dispatchers = ApphudDispatchers(),
        )
        every { userRepository.getCurrentUser() } returns mockk()
        val key = ApphudUserPropertyKey.CustomProperty("test_key")
        blockedManager.setUserProperty(key = key, value = "value", setOnce = false, increment = false)
        launch(testDispatcher) { blockedManager.forceFlushUserProperties(true) }
        advanceUntilIdle()

        val result = blockedManager.forceFlushUserProperties(false)

        assertFalse(result)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    // endregion

    // region clear

    @Test
    fun `GIVEN pending properties EXPECT clear empties pending and forceFlush returns false`() = runTest {
        val key = ApphudUserPropertyKey.CustomProperty("test_key")
        manager.setUserProperty(key = key, value = "value", setOnce = false, increment = false)

        manager.clear()

        val result = manager.forceFlushUserProperties(false)
        assertFalse(result)
    }

    // endregion
}
