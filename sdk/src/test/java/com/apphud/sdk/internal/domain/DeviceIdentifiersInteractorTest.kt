package com.apphud.sdk.internal.domain

import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeviceIdentifiersInteractorTest {

    private val collectUseCase: CollectDeviceIdentifiersUseCase = mockk()
    private val registrationInteractor: RegistrationInteractor = mockk()
    private val deviceIdentifiersRepository: DeviceIdentifiersRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val interactor = DeviceIdentifiersInteractor(
        collectUseCase = collectUseCase,
        registrationInteractor = registrationInteractor,
        deviceIdentifiersRepository = deviceIdentifiersRepository,
        userRepository = userRepository,
    )

    private val testUserId = "test-user-id"
    private val testUser = ApphudUser(
        userId = testUserId,
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

    private val syncedPairs = mutableSetOf<Pair<String, DeviceIdentifiers>>()

    @Before
    fun setup() {
        syncedPairs.clear()
        every { userRepository.getUserId() } returns testUserId
        every { deviceIdentifiersRepository.getIdentifiers() } returns cachedIdentifiers
        every { deviceIdentifiersRepository.isSyncedForUser(any(), any()) } answers {
            val userId = firstArg<String?>()
            val identifiers = secondArg<DeviceIdentifiers>()
            userId != null && syncedPairs.contains(userId to identifiers)
        }
        every { deviceIdentifiersRepository.markSynced(any(), any()) } answers {
            syncedPairs.add(firstArg<String>() to secondArg<DeviceIdentifiers>())
        }
        coEvery { registrationInteractor(any(), any(), any(), any(), any()) } returns testUser
    }

    // region fetch completes in time

    @Test
    fun `GIVEN fetch in time and identifiers not synced EXPECT registrationInteractor called once`() = runTest {
        coEvery { collectUseCase() } returns true

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationInteractor(any(), any(), any(), any(), any()) }
        verify { deviceIdentifiersRepository.markSynced(testUserId, cachedIdentifiers) }
    }

    @Test
    fun `GIVEN fetch in time and identifiers not synced EXPECT returns ApphudUser`() = runTest {
        coEvery { collectUseCase() } returns true

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertEquals(testUser, result)
    }

    @Test
    fun `GIVEN fetch in time and identifiers already synced EXPECT registrationInteractor not called`() = runTest {
        syncedPairs.add(testUserId to cachedIdentifiers)
        coEvery { collectUseCase() } returns false

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 0) { registrationInteractor(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch in time and identifiers already synced EXPECT returns null`() = runTest {
        syncedPairs.add(testUserId to cachedIdentifiers)
        coEvery { collectUseCase() } returns false

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertNull(result)
    }

    // endregion

    // region fetch timeout — cache populated (returning user)

    @Test
    fun `GIVEN fetch timeout, cached IDs not synced and identifiers changed EXPECT registrationInteractor called twice`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); true }

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 2) { registrationInteractor(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, cached IDs not synced and identifiers changed EXPECT returns ApphudUser`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); true }

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertEquals(testUser, result)
    }

    @Test
    fun `GIVEN fetch timeout, cached IDs not synced and identifiers not changed EXPECT registrationInteractor called once`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); false }

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationInteractor(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, cached IDs not synced and identifiers not changed EXPECT returns null`() = runTest {
        coEvery { collectUseCase() } coAnswers { delay(2000); false }

        val result = interactor(this, needPlacementsPaywalls = false, isNew = false)

        assertNull(result)
    }

    @Test
    fun `GIVEN fetch timeout and cached IDs already synced EXPECT registrationInteractor not called`() = runTest {
        syncedPairs.add(testUserId to cachedIdentifiers)
        coEvery { collectUseCase() } coAnswers { delay(2000); false }

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 0) { registrationInteractor(any(), any(), any(), any(), any()) }
    }

    // endregion

    // region fetch timeout — empty cache (first install)

    @Test
    fun `GIVEN fetch timeout, no cached IDs and identifiers changed EXPECT registrationInteractor called once`() = runTest {
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers.EMPTY
        coEvery { collectUseCase() } coAnswers { delay(2000); true }
        every { deviceIdentifiersRepository.getIdentifiers() } returnsMany listOf(
            DeviceIdentifiers.EMPTY,
            cachedIdentifiers,
        )

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 1) { registrationInteractor(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GIVEN fetch timeout, no cached IDs and identifiers not changed EXPECT registrationInteractor not called`() = runTest {
        every { deviceIdentifiersRepository.getIdentifiers() } returns DeviceIdentifiers.EMPTY
        coEvery { collectUseCase() } coAnswers { delay(2000); false }

        interactor(this, needPlacementsPaywalls = false, isNew = false)

        coVerify(exactly = 0) { registrationInteractor(any(), any(), any(), any(), any()) }
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
