package com.apphud.sdk.internal.data

import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReinstallRepositoryTest {

    private class FakeStorage : InstallRecordStorage {
        private var record: InstallRecord? = null
        override fun read(): InstallRecord? = record
        override fun write(record: InstallRecord) {
            this.record = record
        }
    }

    private class FakeMarkerSource(
        private val stored: ByteArray? = null,
        private val readError: Exception? = null,
    ) : InstallMarkerSource {
        override suspend fun read(): ByteArray? = readError?.let { throw it } ?: stored
        override suspend fun write(marker: ByteArray) = Unit
    }

    private val storage = FakeStorage()

    /** A launch over the same storage. */
    private fun CoroutineScope.launch(markerSource: InstallMarkerSource) =
        ReinstallRepository(
            storage = storage,
            markerSource = markerSource,
            deviceMarker = { THIS_DEVICE },
            isFreshPackageInstall = { true },
            scope = this,
        )

    // region new install

    @Test
    fun `GIVEN no marker EXPECT first install`() = runTest {
        assertEquals(false, launch(FakeMarkerSource()).awaitIsReinstall())
    }

    @Test
    fun `GIVEN this device's marker EXPECT reinstall`() = runTest {
        assertEquals(true, launch(FakeMarkerSource(stored = THIS_DEVICE)).awaitIsReinstall())
    }

    @Test
    fun `GIVEN marker copied from another device EXPECT first install`() = runTest {
        assertEquals(false, launch(FakeMarkerSource(stored = OTHER_DEVICE)).awaitIsReinstall())
    }

    @Test
    fun `GIVEN marker read fails EXPECT unknown`() = runTest {
        val markerSource = FakeMarkerSource(readError = IllegalStateException("no Play services"))

        assertNull(launch(markerSource).awaitIsReinstall())
    }

    // endregion

    // region registration flag

    @Test
    fun `GIVEN reinstall WHEN flag sent EXPECT no flag after relaunch`() = runTest {
        val repository = launch(FakeMarkerSource(stored = THIS_DEVICE))
        repository.awaitIsReinstall()

        repository.onReinstallSent()
        advanceUntilIdle()

        val relaunch = launch(FakeMarkerSource(stored = THIS_DEVICE))
        relaunch.awaitIsReinstall()
        assertNull(relaunch.pendingReinstallFlag())
    }

    // endregion

    private companion object {
        val THIS_DEVICE = ReinstallRepository.deviceMarkerOf("this-device")!!
        val OTHER_DEVICE = ReinstallRepository.deviceMarkerOf("other-device")!!
    }
}
