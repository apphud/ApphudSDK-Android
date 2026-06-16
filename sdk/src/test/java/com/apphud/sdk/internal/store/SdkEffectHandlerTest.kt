package com.apphud.sdk.internal.store

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.SdkRegistrationState
import com.apphud.sdk.internal.domain.RegistrationInteractor
import com.apphud.sdk.storage.SharedPreferencesStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SdkEffectHandlerTest {

    private fun testUser() = ApphudUser(
        userId = "test-user",
        currencyCode = "USD",
        countryCode = "US",
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = false,
    )

    private fun handler(
        coroutineScope: CoroutineScope,
        registrationState: SdkRegistrationState,
        registrationInteractor: RegistrationInteractor,
    ): SdkEffectHandler {
        val storage: SharedPreferencesStorage = mockk {
            every { isNeedSync } returns false
        }
        return SdkEffectHandler(
            registrationInteractor = registrationInteractor,
            userRepository = mockk(),
            analyticsTracker = mockk(relaxed = true),
            userPropertiesManager = mockk(relaxed = true),
            fetchNativePurchasesUseCase = mockk(),
            storage = storage,
            coroutineScope = coroutineScope,
            registrationState = registrationState,
        )
    }

    @Test
    fun `GIVEN force registration EXPECT needPlacementsPaywalls is true`() = runTest {
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } returns testUser()
        }
        val handler = handler(
            coroutineScope = this,
            registrationState = SdkRegistrationState(observerMode = false),
            registrationInteractor = registrationInteractor,
        )

        handler.handle(SdkEffect.PerformRegistration(isForce = true, isNew = false)) {}

        coVerify {
            registrationInteractor.invoke(
                needPlacementsPaywalls = true,
                isNew = false,
                forceRegistration = true,
                userId = null,
                email = null,
            )
        }
    }

    @Test
    fun `GIVEN deferred placements force registration EXPECT needPlacementsPaywalls is false`() = runTest {
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } returns testUser()
        }
        val registrationState = SdkRegistrationState(observerMode = false)
        registrationState.setDeferPlacements(true)
        val handler = handler(
            coroutineScope = this,
            registrationState = registrationState,
            registrationInteractor = registrationInteractor,
        )

        handler.handle(SdkEffect.PerformRegistration(isForce = true, isNew = false)) {}

        coVerify {
            registrationInteractor.invoke(
                needPlacementsPaywalls = false,
                isNew = false,
                forceRegistration = true,
                userId = null,
                email = null,
            )
        }
    }

    @Test
    fun `GIVEN already registered customer force registration EXPECT needPlacementsPaywalls is false`() = runTest {
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } returns testUser()
        }
        val registrationState = SdkRegistrationState(observerMode = false)
        registrationState.markCustomerRegisteredAtThisLaunch(true)
        val handler = handler(
            coroutineScope = this,
            registrationState = registrationState,
            registrationInteractor = registrationInteractor,
        )

        handler.handle(SdkEffect.PerformRegistration(isForce = true, isNew = false)) {}

        coVerify {
            registrationInteractor.invoke(
                needPlacementsPaywalls = false,
                isNew = false,
                forceRegistration = true,
                userId = null,
                email = null,
            )
        }
    }

    @Test
    fun `GIVEN observer mode force registration EXPECT needPlacementsPaywalls is false`() = runTest {
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } returns testUser()
        }
        val handler = handler(
            coroutineScope = this,
            registrationState = SdkRegistrationState(observerMode = true),
            registrationInteractor = registrationInteractor,
        )

        handler.handle(SdkEffect.PerformRegistration(isForce = true, isNew = false)) {}

        coVerify {
            registrationInteractor.invoke(
                needPlacementsPaywalls = false,
                isNew = false,
                forceRegistration = true,
                userId = null,
                email = null,
            )
        }
    }

    @Test
    fun `GIVEN deferred placements regular registration EXPECT needPlacementsPaywalls is false`() = runTest {
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } returns testUser()
        }
        val registrationState = SdkRegistrationState(observerMode = false)
        registrationState.setDeferPlacements(true)
        val handler = handler(
            coroutineScope = this,
            registrationState = registrationState,
            registrationInteractor = registrationInteractor,
        )

        handler.handle(SdkEffect.PerformRegistration(isForce = false, isNew = true)) {}

        coVerify {
            registrationInteractor.invoke(
                needPlacementsPaywalls = false,
                isNew = true,
                forceRegistration = false,
                userId = null,
                email = null,
            )
        }
    }

    @Test
    fun `GIVEN concurrent registrations EXPECT registrationInteractor is not entered concurrently`() = runTest {
        val activeCalls = AtomicInteger(0)
        val maxActiveCalls = AtomicInteger(0)
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } coAnswers {
                val active = activeCalls.incrementAndGet()
                maxActiveCalls.updateAndGet { current -> maxOf(current, active) }
                delay(100)
                activeCalls.decrementAndGet()
                testUser()
            }
        }
        val handler = handler(
            coroutineScope = this,
            registrationState = SdkRegistrationState(observerMode = false),
            registrationInteractor = registrationInteractor,
        )

        val first = async { handler.handle(SdkEffect.PerformRegistration(isForce = true, isNew = false)) {} }
        val second = async { handler.handle(SdkEffect.PerformRegistration(isForce = true, isNew = false)) {} }
        first.await()
        second.await()

        org.junit.Assert.assertEquals(1, maxActiveCalls.get())
    }

    @Test
    fun `GIVEN sequential registrations EXPECT second needPlacementsPaywalls uses updated registration state`() = runTest {
        val requestedNeedPlacements = mutableListOf<Boolean>()
        val registrationState = SdkRegistrationState(observerMode = false)
        val registrationInteractor: RegistrationInteractor = mockk {
            coEvery { this@mockk.invoke(any(), any(), any(), any(), any()) } coAnswers {
                requestedNeedPlacements.add(firstArg())
                testUser()
            }
        }
        val handler = handler(
            coroutineScope = this,
            registrationState = registrationState,
            registrationInteractor = registrationInteractor,
        )
        val dispatch: (SdkEvent) -> Unit = { event ->
            if (event is SdkEvent.RegistrationSucceeded) {
                registrationState.markCustomerRegisteredAtThisLaunch(true)
            }
        }

        val first = async { handler.handle(SdkEffect.PerformRegistration(isForce = false, isNew = true), dispatch) }
        val second = async { handler.handle(SdkEffect.PerformRegistration(isForce = false, isNew = true), dispatch) }
        first.await()
        second.await()

        org.junit.Assert.assertEquals(listOf(true, false), requestedNeedPlacements)
    }
}
