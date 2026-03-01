package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdentifiersInteractorTest {

    private val collectUseCase: CollectDeviceIdentifiersUseCase = mockk()
    private val registrationUseCase: RegistrationUseCase = mockk()
    private val interactor = DeviceIdentifiersInteractor(collectUseCase, registrationUseCase)

    private val testUser = ApphudUser(
        userId = "test-user-id",
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
        paywalls = emptyList(),
        placements = emptyList(),
        isTemporary = false,
    )

    // region fetch completes in time

    @Test
    fun `GIVEN fetch in time and identifiers changed EXPECT registrationUseCase called once`() = runTest {
        coEvery { collectUseCase() } returns true
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch in time and identifiers changed EXPECT returns ApphudUser`() = runTest {
        coEvery { collectUseCase() } returns true
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertEquals(testUser, result)
    }

    @Test
    fun `GIVEN fetch in time and identifiers not changed EXPECT registrationUseCase not called`() = runTest {
        coEvery { collectUseCase() } returns false

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 0) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch in time and identifiers not changed EXPECT returns null`() = runTest {
        coEvery { collectUseCase() } returns false

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertNull(result)
    }

    // endregion

    // region fetch timeout

    @Test
    fun `GIVEN fetch timeout and identifiers changed EXPECT registrationUseCase called twice`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); true }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 2) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout and identifiers changed EXPECT returns ApphudUser`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); true }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertEquals(testUser, result)
    }

    @Test
    fun `GIVEN fetch timeout and identifiers not changed EXPECT registrationUseCase called once`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); false }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout and identifiers not changed EXPECT returns null`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); false }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertNull(result)
    }

    // endregion
}
