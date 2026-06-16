package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.store.SdkEffect
import com.apphud.sdk.internal.store.SdkEvent
import com.apphud.sdk.internal.store.SdkState
import com.apphud.sdk.internal.store.Store
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaitRegistrationUseCaseTest {

    private fun testUser(isTemporary: Boolean? = false) = ApphudUser(
        userId = "test-user",
        currencyCode = "USD",
        countryCode = "US",
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = isTemporary,
    )

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    // region NotInitialized state

    @Test
    fun `GIVEN state is NotInitialized EXPECT throws ApphudError`() = runTest {
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.NotInitialized,
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk()
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertTrue(exception is ApphudError)
    }

    @Test
    fun `GIVEN state is NotInitialized EXPECT error message contains Apphud start`() = runTest {
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.NotInitialized,
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk()
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertTrue(exception?.message?.contains("Apphud.start") == true)
    }

    // endregion

    // region registered user (not temporary)

    @Test
    fun `GIVEN registered user EXPECT returns immediately without error`() = runTest {
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Registering(apiKey = "test", userId = "user-1"),
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns testUser(isTemporary = false)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val result = runCatching { useCase() }

        assertTrue(result.isSuccess)
    }

    // endregion

    // region null user — awaiting registration

    @Test
    fun `GIVEN null user and state is already Ready EXPECT completes successfully`() = runTest {
        val registeredUser = testUser(isTemporary = false)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = registeredUser),
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returnsMany listOf(null, registeredUser)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val result = runCatching { useCase() }

        assertTrue(result.isSuccess)
    }

    @Test
    fun `GIVEN null user and state is Ready but second getCurrentUser returns null EXPECT throws`() = runTest {
        val readyUser = testUser(isTemporary = false)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = readyUser),
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns null
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertTrue(exception is ApphudError)
    }

    @Test
    fun `GIVEN null user and state is Ready but second getCurrentUser returns null EXPECT error message is Registration failed`() = runTest {
        val readyUser = testUser(isTemporary = false)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = readyUser),
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns null
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertEquals("Registration failed", exception?.message)
    }

    @Test
    fun `GIVEN null user and state is Degraded with valid user EXPECT completes successfully`() = runTest {
        val registeredUser = testUser(isTemporary = false)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Degraded(apiKey = "test", user = null, lastError = null),
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returnsMany listOf(null, registeredUser)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val result = runCatching { useCase() }

        assertTrue(result.isSuccess)
    }

    // endregion

    // region temporary user — force registration

    @Test
    fun `GIVEN temporary user EXPECT dispatches ForceRegistrationRequested`() = runTest {
        val registeredUser = testUser(isTemporary = false)
        val capturedEvents = mutableListOf<SdkEvent>()
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "api-key", user = testUser(isTemporary = true)),
            reducer = { state, event ->
                capturedEvents.add(event)
                SdkState.Ready(apiKey = "api-key", user = registeredUser) to emptyList()
            },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returnsMany listOf(testUser(isTemporary = true), registeredUser)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        useCase()

        assertTrue(capturedEvents.any { it is SdkEvent.ForceRegistrationRequested })
    }

    @Test
    fun `GIVEN temporary user EXPECT ForceRegistrationRequested carries apiKey from current state`() = runTest {
        val registeredUser = testUser(isTemporary = false)
        val capturedEvents = mutableListOf<SdkEvent>()
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "api-key", user = testUser(isTemporary = true)),
            reducer = { state, event ->
                capturedEvents.add(event)
                SdkState.Ready(apiKey = "api-key", user = registeredUser) to emptyList()
            },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returnsMany listOf(testUser(isTemporary = true), registeredUser)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        useCase()

        val event = capturedEvents.filterIsInstance<SdkEvent.ForceRegistrationRequested>().single()
        assertEquals("api-key", event.apiKey)
    }

    @Test
    fun `GIVEN temporary user and registration succeeds EXPECT completes without error`() = runTest {
        val registeredUser = testUser(isTemporary = false)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = testUser(isTemporary = true)),
            reducer = { _, _ -> SdkState.Ready(apiKey = "test", user = registeredUser) to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returnsMany listOf(testUser(isTemporary = true), registeredUser)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val result = runCatching { useCase() }

        assertTrue(result.isSuccess)
    }

    @Test
    fun `GIVEN temporary user and registration fails EXPECT throws`() = runTest {
        val temporaryUser = testUser(isTemporary = true)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = temporaryUser),
            reducer = { state, _ -> SdkState.Ready(apiKey = "test", user = temporaryUser) to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns temporaryUser
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertTrue(exception is ApphudError)
    }

    // endregion

    // region state becomes Degraded

    @Test
    fun `GIVEN null user and state becomes Degraded with temporary user EXPECT throws`() = runTest {
        val temporaryUser = testUser(isTemporary = true)
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Degraded(apiKey = "test", user = null, lastError = null),
            reducer = { state, _ -> state to emptyList() },
            effectHandler = { _, _ -> },
            scope = testScope(),
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns temporaryUser
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertTrue(exception is ApphudError)
    }

    // endregion
}
