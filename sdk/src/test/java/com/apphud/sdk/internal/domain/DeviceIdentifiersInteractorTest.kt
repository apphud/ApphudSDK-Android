package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeviceIdentifiersInteractorTest {

    private val collectUseCase: CollectDeviceIdentifiersUseCase = mockk()
    private val registrationUseCase: RegistrationUseCase = mockk()
    private val deviceIdentifiersRepository: DeviceIdentifiersRepository = mockk()
    private val interactor = DeviceIdentifiersInteractor(
        collectUseCase = collectUseCase,
        registrationUseCase = registrationUseCase,
        deviceIdentifiersRepository = deviceIdentifiersRepository,
    )

    private val testUser = ApphudUser(
        userId = "test-user-id",
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = emptyList(),
        isTemporary = false,
    )

    private val cachedIdentifiers = DeviceIdentifiers(
        advertisingId = "cachedAdId",
        appSetId = "cachedAppSetId",
        androidId = "cachedAndroidId",
    )

    @Before
    fun setup() {
        // Default: cache has identifiers (returning user). Tests that exercise the
        // "no cached IDs" path override this explicitly.
        every { deviceIdentifiersRepository.getIdentifiers() } returns cachedIdentifiers
    }

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

    // region fetch timeout — cache populated (returning user)

    @Test
    fun `GIVEN fetch timeout, cached IDs and identifiers changed EXPECT registrationUseCase called twice`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); true }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 2) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, cached IDs and identifiers changed EXPECT returns ApphudUser`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); true }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertEquals(testUser, result)
    }

    @Test
    fun `GIVEN fetch timeout, cached IDs and identifiers not changed EXPECT registrationUseCase called once`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); false }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, cached IDs and identifiers not changed EXPECT returns null`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); false }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertNull(result)
    }

    // endregion

    // region fetch timeout — empty cache (first install)

    @Test
    fun `GIVEN fetch timeout, no cached IDs and identifiers changed EXPECT registrationUseCase called once`() = runTest {
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers.EMPTY
        coEvery { collectUseCase() } coAnswers { delay(2000); true }
        coEvery { registrationUseCase(any(), any(), any(), any(), any()) } returns testUser

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, no cached IDs and identifiers not changed EXPECT registrationUseCase not called`() = runTest {
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers.EMPTY
        coEvery { collectUseCase() } coAnswers { delay(2000); false }

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 0) { registrationUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, no cached IDs and identifiers not changed EXPECT returns null`() = runTest {
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers.EMPTY
        coEvery { collectUseCase() } coAnswers { delay(2000); false }

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertNull(result)
    }

    // endregion
}
