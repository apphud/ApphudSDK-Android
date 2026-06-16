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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `GIVEN temporary user EXPECT ForceRegistrationRequested has no user overrides`() = runTest {
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
        assertEquals(null, event.userId)
        assertEquals(null, event.email)
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
    fun `GIVEN temporary user and current state is Ready EXPECT waits for next terminal state`() = runTest {
        val temporaryUser = testUser(isTemporary = true)
        val registeredUser = testUser(isTemporary = false)
        var currentUser = temporaryUser
        var completed = false
        val capturedEvents = mutableListOf<SdkEvent>()
        val storeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "api-key", user = temporaryUser, fromFallback = true),
            reducer = { state, event ->
                capturedEvents.add(event)
                when (event) {
                    is SdkEvent.ForceRegistrationRequested -> SdkState.Registering(apiKey = "api-key", userId = null, isForce = true) to emptyList()
                    is SdkEvent.RegistrationSucceeded -> SdkState.Ready(apiKey = "api-key", user = registeredUser) to emptyList()
                    else -> state to emptyList()
                }
            },
            effectHandler = { _, _ -> },
            scope = storeScope,
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } answers { currentUser }
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        try {
            val job = launch {
                useCase()
                completed = true
            }
            runCurrent()

            assertTrue(capturedEvents.any { it is SdkEvent.ForceRegistrationRequested })
            assertFalse(completed)

            currentUser = registeredUser
            store.dispatch(SdkEvent.RegistrationSucceeded(registeredUser))
            runCurrent()

            assertTrue(completed)
            job.cancel()
        } finally {
            storeScope.cancel()
        }
    }

    @Test
    fun `GIVEN temporary user and registration fails EXPECT throws`() = runTest {
        val temporaryUser = testUser(isTemporary = true)
        var exception: Throwable? = null
        val storeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = temporaryUser),
            reducer = { state, event ->
                when (event) {
                    is SdkEvent.ForceRegistrationRequested -> {
                        SdkState.Degraded(apiKey = "test", user = temporaryUser, lastError = null, fromFallback = true) to emptyList()
                    }
                    else -> state to emptyList()
                }
            },
            effectHandler = { _, _ -> },
            scope = storeScope,
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns temporaryUser
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        try {
            launch {
                exception = runCatching { useCase() }.exceptionOrNull()
            }
            advanceUntilIdle()
        } finally {
            storeScope.cancel()
        }

        assertTrue(exception is ApphudError)
    }

    @Test
    fun `GIVEN temporary user after next terminal state EXPECT throws Registration failed`() = runTest {
        val temporaryUser = testUser(isTemporary = true)
        var exception: Throwable? = null
        val storeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = Store<SdkState, SdkEvent, SdkEffect>(
            initialState = SdkState.Ready(apiKey = "test", user = temporaryUser),
            reducer = { state, event ->
                when (event) {
                    is SdkEvent.ForceRegistrationRequested -> {
                        SdkState.Degraded(apiKey = "test", user = temporaryUser, lastError = null, fromFallback = true) to emptyList()
                    }
                    else -> state to emptyList()
                }
            },
            effectHandler = { _, _ -> },
            scope = storeScope,
        )
        val userRepository: UserRepository = mockk {
            every { getCurrentUser() } returns temporaryUser
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        try {
            launch {
                exception = runCatching { useCase() }.exceptionOrNull()
            }
            advanceUntilIdle()
        } finally {
            storeScope.cancel()
        }

        assertEquals("Registration failed", exception?.message)
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
            every { getCurrentUser() } returnsMany listOf(null, temporaryUser)
        }
        val useCase = AwaitRegistrationUseCase(store, userRepository)

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertTrue(exception is ApphudError)
    }

    // endregion
}
