package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectDeviceIdentifiersUseCaseTest {

    private lateinit var repository: DeviceIdentifiersRepository
    private lateinit var useCase: CollectDeviceIdentifiersUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = CollectDeviceIdentifiersUseCase(repository)
        mockkObject(ApphudUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(ApphudUtils)
    }

    // region optOutOfTracking

    @Test
    fun `GIVEN optOutOfTracking is true EXPECT returns false`() = runTest {
        every { ApphudUtils.optOutOfTracking } returns true

        val result = useCase()

        assertFalse(result)
    }

    @Test
    fun `GIVEN optOutOfTracking is true EXPECT does not fetch identifiers`() = runTest {
        every { ApphudUtils.optOutOfTracking } returns true

        useCase()

        coVerify(exactly = 0) { repository.fetchAndUpdateIdentifiers() }
    }

    // endregion

    // region identifiers changed

    @Test
    fun `GIVEN old and new identifiers differ EXPECT returns true`() = runTest {
        every { ApphudUtils.optOutOfTracking } returns false
        val oldIdentifiers = DeviceIdentifiers(advertisingId = "oldAdId", appSetId = null, androidId = null)
        val newIdentifiers = DeviceIdentifiers(advertisingId = "newAdId", appSetId = null, androidId = null)
        every { repository.getIdentifiers() } returns oldIdentifiers
        coEvery { repository.fetchAndUpdateIdentifiers() } returns newIdentifiers

        val result = useCase()

        assertTrue(result)
    }

    @Test
    fun `GIVEN old and new identifiers equal EXPECT returns false`() = runTest {
        every { ApphudUtils.optOutOfTracking } returns false
        val identifiers = DeviceIdentifiers(advertisingId = "adId", appSetId = "appSetId", androidId = "androidId")
        every { repository.getIdentifiers() } returns identifiers
        coEvery { repository.fetchAndUpdateIdentifiers() } returns identifiers

        val result = useCase()

        assertFalse(result)
    }

    // endregion
}
