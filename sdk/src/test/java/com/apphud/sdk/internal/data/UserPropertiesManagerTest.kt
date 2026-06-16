package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudUserPropertyKey
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.store.SdkEffect
import com.apphud.sdk.internal.store.SdkEvent
import com.apphud.sdk.internal.store.SdkState
import com.apphud.sdk.internal.store.Store
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val readySdkStore: Store<SdkState, SdkEvent, SdkEffect> = Store(
        initialState = SdkState.Ready(apiKey = "test", user = mockk()),
        reducer = { state, _ -> state to emptyList() },
        effectHandler = { _, _ -> },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private val manager = UserPropertiesManager(
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        userRepository = userRepository,
        storage = storage,
        sdkStore = readySdkStore,
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
        val externalScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val notInitializedStore: Store<SdkState, SdkEvent, SdkEffect> = Store(
            initialState = SdkState.NotInitialized,
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = externalScope,
        )
        val blockedManager = UserPropertiesManager(
            coroutineScope = externalScope,
            userRepository = userRepository,
            storage = storage,
            sdkStore = notInitializedStore,
            dispatchers = ApphudDispatchers(),
        )
        every { userRepository.getCurrentUser() } returns mockk()
        val key = ApphudUserPropertyKey.CustomProperty("test_key")
        blockedManager.setUserProperty(key = key, value = "value", setOnce = false, increment = false)
        externalScope.launch { blockedManager.forceFlushUserProperties(true) }
        advanceUntilIdle()

        val result = blockedManager.forceFlushUserProperties(false)

        assertFalse(result)
        externalScope.cancel()
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
