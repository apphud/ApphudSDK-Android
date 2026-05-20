package com.apphud.sdk.internal.data

import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import com.apphud.sdk.internal.domain.model.SyncedDeviceIdentifiers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeviceIdentifiersRepositoryTest {

    private lateinit var dataSource: DeviceIdentifiersDataSource
    private lateinit var repository: DeviceIdentifiersRepository

    private val defaultIdentifiers = DeviceIdentifiers(
        advertisingId = "adId123",
        appSetId = "appSetId456",
        androidId = "androidId789",
    )

    @Before
    fun setup() {
        dataSource = mockk(relaxed = true)
        every { dataSource.loadCached() } returns defaultIdentifiers
        repository = DeviceIdentifiersRepository(dataSource)
    }

    // region initialization

    @Test
    fun `GIVEN cached identifiers EXPECT getIdentifiers returns cached`() {
        val dataSource = mockk<DeviceIdentifiersDataSource>(relaxed = true)
        every { dataSource.loadCached() } returns defaultIdentifiers
        val repository = DeviceIdentifiersRepository(dataSource)

        val result = repository.getIdentifiers()

        assertEquals(defaultIdentifiers, result)
    }

    @Test
    fun `GIVEN empty cache EXPECT getIdentifiers returns EMPTY`() {
        val dataSource = mockk<DeviceIdentifiersDataSource>(relaxed = true)
        every { dataSource.loadCached() } returns DeviceIdentifiers.EMPTY
        val repository = DeviceIdentifiersRepository(dataSource)

        val result = repository.getIdentifiers()

        assertEquals(DeviceIdentifiers.EMPTY, result)
    }

    // endregion

    // region fetchAndUpdateIdentifiers

    @Test
    fun `GIVEN dataSource returns new identifiers EXPECT getIdentifiers returns new`() = runTest {
        val newIdentifiers = DeviceIdentifiers(advertisingId = "newAdId", appSetId = "newAppSetId", androidId = "newAndroidId")
        coEvery { dataSource.fetchIdentifiers() } returns newIdentifiers
        every { dataSource.loadCached() } returns newIdentifiers

        repository.fetchAndUpdateIdentifiers()

        assertEquals(newIdentifiers, repository.getIdentifiers())
    }

    @Test
    fun `GIVEN dataSource returns new identifiers EXPECT saves to dataSource`() = runTest {
        val newIdentifiers = DeviceIdentifiers(advertisingId = "newAdId", appSetId = "newAppSetId", androidId = "newAndroidId")
        coEvery { dataSource.fetchIdentifiers() } returns newIdentifiers

        repository.fetchAndUpdateIdentifiers()

        verify { dataSource.save(newIdentifiers) }
    }

    // endregion

    // region clear

    @Test
    fun `GIVEN clear called EXPECT saves EMPTY to dataSource`() {
        repository.clear()

        verify { dataSource.save(DeviceIdentifiers.EMPTY) }
        verify { dataSource.clearSyncedState() }
    }

    // endregion

    // region sync state

    @Test
    fun `GIVEN synced state matches user and identifiers EXPECT isSyncedForUser true`() {
        every { dataSource.loadSyncedState() } returns SyncedDeviceIdentifiers(
            userId = "user-1",
            identifiers = defaultIdentifiers,
        )

        assertEquals(true, repository.isSyncedForUser("user-1", defaultIdentifiers))
    }

    @Test
    fun `GIVEN synced state for different user EXPECT isSyncedForUser false`() {
        every { dataSource.loadSyncedState() } returns SyncedDeviceIdentifiers(
            userId = "user-1",
            identifiers = defaultIdentifiers,
        )

        assertEquals(false, repository.isSyncedForUser("user-2", defaultIdentifiers))
    }

    @Test
    fun `GIVEN markSynced called EXPECT saves synced state to dataSource`() {
        repository.markSynced("user-1", defaultIdentifiers)

        verify { dataSource.saveSyncedState("user-1", defaultIdentifiers) }
    }

    // endregion

    // region fetchAndroidIdSync

    @Test
    fun `GIVEN dataSource returns androidId EXPECT fetchAndroidIdSync delegates to dataSource`() {
        every { dataSource.fetchAndroidIdSync() } returns "android-id-123"

        val result = repository.fetchAndroidIdSync()

        assertEquals("android-id-123", result)
    }

    // endregion
}
