package com.apphud.sdk.internal.data

import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import com.apphud.sdk.storage.InstallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReinstallRepositoryTest {

    private class FakeStorage : InstallRecordStorage {
        var record: InstallRecord? = null
        override fun read(): InstallRecord? = record
        override fun write(record: InstallRecord) {
            this.record = record
        }
    }

    /** The marker store that outlives uninstall, shared by the launches of one device. */
    private class FakeMarkerSource(
        var stored: ByteArray? = null,
        var readError: Exception? = null,
        var neverAnswers: Boolean = false,
    ) : InstallMarkerSource {
        var reads = 0
        val writes = mutableListOf<ByteArray>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(): ByteArray? {
            reads += 1
            if (neverAnswers) awaitCancellation()
            gate?.await()
            readError?.let { throw it }
            return stored
        }

        override suspend fun write(marker: ByteArray) {
            writes += marker
            stored = marker
        }
    }

    private val storage = FakeStorage()
    private val markers = FakeMarkerSource()
    private var decidedCalls = 0

    /** A launch over the same storage. A new install's record is created first, as identity resolution does. */
    private fun CoroutineScope.launch(
        state: InstallState? = null,
        start: Boolean = true,
    ): ReinstallRepository {
        val newInstall = storage.record == null
        if (newInstall) storage.write(InstallRecord(state ?: InstallState.PENDING, "installation-1"))
        return ReinstallRepository(
            storage = storage,
            markerSource = markers,
            deviceMarker = { THIS_DEVICE },
            scope = this,
            onDecided = { decidedCalls += 1 },
        ).also { if (start) it.start(newInstall) }
    }

    // region new install

    @Test
    fun `GIVEN no marker EXPECT first install`() = runTest {
        assertEquals(false, launch().awaitIsReinstall())
    }

    @Test
    fun `GIVEN this device's marker EXPECT reinstall`() = runTest {
        markers.stored = THIS_DEVICE

        assertEquals(true, launch().awaitIsReinstall())
    }

    @Test
    fun `GIVEN marker copied from another device EXPECT first install`() = runTest {
        markers.stored = OTHER_DEVICE

        assertEquals(false, launch().awaitIsReinstall())
    }

    @Test
    fun `GIVEN marker read fails EXPECT unknown`() = runTest {
        markers.readError = IllegalStateException("no Play services")

        assertNull(launch().awaitIsReinstall())
    }

    @Test
    fun `GIVEN marker read fails EXPECT marker not written`() = runTest {
        markers.readError = IllegalStateException("no Play services")
        launch().awaitIsReinstall()
        advanceUntilIdle()

        assertTrue(markers.writes.isEmpty())
    }

    @Test
    fun `GIVEN first install decided EXPECT this device's marker written`() = runTest {
        launch().awaitIsReinstall()
        advanceUntilIdle()

        assertArrayEquals(THIS_DEVICE, markers.stored)
    }

    @Test
    fun `GIVEN new install decided EXPECT the next registration told`() = runTest {
        launch().awaitIsReinstall()

        assertEquals(1, decidedCalls)
    }

    @Test
    fun `GIVEN marker read never answers EXPECT registration waits at most the bound`() = runTest {
        markers.neverAnswers = true

        launch().awaitIsReinstall()

        assertEquals(5_000L, currentTime)
    }

    // endregion

    // region later launches

    @Test
    fun `GIVEN failed read WHEN a later launch reads EXPECT the first successful read decides`() = runTest {
        markers.readError = IllegalStateException("Block Store failed")
        launch().awaitIsReinstall()
        advanceUntilIdle()
        markers.readError = null
        markers.stored = THIS_DEVICE.copyOf()

        val relaunch = launch()
        advanceUntilIdle()

        assertEquals(true, relaunch.isReinstall())
    }

    @Test
    fun `GIVEN pending from an earlier launch EXPECT registration not held for the read`() = runTest {
        launch(start = false)
        markers.neverAnswers = true

        launch().awaitIsReinstall()

        assertEquals(0L, currentTime)
    }

    @Test
    fun `GIVEN reinstall EXPECT flag on every registration after relaunch`() = runTest {
        markers.stored = THIS_DEVICE
        launch().awaitIsReinstall()
        advanceUntilIdle()

        val relaunch = launch()
        relaunch.awaitIsReinstall()
        assertEquals(true, relaunch.reinstallFlag())
    }

    @Test
    fun `GIVEN first install EXPECT no reinstall flag`() = runTest {
        val repository = launch()
        repository.awaitIsReinstall()

        assertNull(repository.reinstallFlag())
    }

    @Test
    fun `GIVEN normal launch with another device's marker EXPECT this device's marker written`() = runTest {
        launch().awaitIsReinstall()
        advanceUntilIdle()
        markers.stored = OTHER_DEVICE

        launch()
        advanceUntilIdle()

        assertArrayEquals(THIS_DEVICE, markers.stored)
    }

    @Test
    fun `GIVEN awaitIsReinstall called before start EXPECT the check still runs at start`() = runTest {
        val repository = ReinstallRepository(storage, markers, { THIS_DEVICE }, this)
        val early = async { repository.awaitIsReinstall() }
        runCurrent()
        storage.write(InstallRecord(InstallState.PENDING, "installation-1"))

        repository.start(newInstall = true)

        assertEquals(false, early.await())
    }

    @Test
    fun `GIVEN the record rewritten while the marker was read EXPECT the decision keeps the new installation id`() = runTest {
        markers.gate = CompletableDeferred()
        val repository = launch()
        runCurrent()
        storage.write(InstallRecord(InstallState.PENDING, "installation-2"))
        markers.gate?.complete(Unit)

        repository.awaitIsReinstall()

        assertEquals("installation-2", storage.record?.installationId)
    }

    // endregion

    // region update in place and supplied flag

    @Test
    fun `GIVEN install updated in place EXPECT unknown`() = runTest {
        markers.stored = THIS_DEVICE

        assertNull(launch(state = InstallState.EXISTING).awaitIsReinstall())
    }

    @Test
    fun `GIVEN install updated in place EXPECT this device's marker written`() = runTest {
        launch(state = InstallState.EXISTING).awaitIsReinstall()
        advanceUntilIdle()

        assertArrayEquals(THIS_DEVICE, markers.stored)
    }

    @Test
    fun `GIVEN the app decides the flag EXPECT its value`() = runTest {
        val repository = launch(start = false)

        repository.useHostValue(true)

        assertEquals(true, repository.awaitIsReinstall())
    }

    @Test
    fun `GIVEN the app decides the flag EXPECT no wait`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(true)

        repository.awaitIsReinstall()

        assertEquals(0L, currentTime)
    }

    @Test
    fun `GIVEN the app decides the flag EXPECT no marker read`() = runTest {
        val repository = launch(start = false)

        repository.useHostValue(false)
        advanceUntilIdle()

        assertEquals(0, markers.reads)
    }

    @Test
    fun `GIVEN check done then the app's flag WHEN a later start without supplied ids EXPECT no wait`() = runTest {
        val repository = launch()
        repository.awaitIsReinstall()
        repository.useHostValue(true)
        repository.start(newInstall = false)
        val before = currentTime

        repository.awaitIsReinstall()

        assertEquals(before, currentTime)
    }

    @Test
    fun `GIVEN check done then the app's flag WHEN a later start without supplied ids EXPECT the SDK's result again`() = runTest {
        val repository = launch()
        repository.awaitIsReinstall()
        repository.useHostValue(true)

        repository.start(newInstall = false)

        assertEquals(false, repository.awaitIsReinstall())
    }

    @Test
    fun `GIVEN the app decided the flag WHEN a later start without supplied ids EXPECT the SDK's own check`() = runTest {
        markers.stored = THIS_DEVICE
        val repository = launch(start = false)
        repository.useHostValue(false)

        repository.start(newInstall = true)

        assertEquals(true, repository.awaitIsReinstall())
    }

    // endregion

    // region marker value

    @Test
    fun `GIVEN no device id EXPECT the constant marker`() {
        assertArrayEquals(ReinstallRepository.CONSTANT_MARKER, ReinstallRepository.deviceMarkerOf(null))
    }

    @Test
    fun `GIVEN blank device id EXPECT the constant marker`() {
        assertArrayEquals(ReinstallRepository.CONSTANT_MARKER, ReinstallRepository.deviceMarkerOf(" "))
    }

    @Test
    fun `GIVEN the id many old devices share EXPECT the constant marker`() {
        assertArrayEquals(ReinstallRepository.CONSTANT_MARKER, ReinstallRepository.deviceMarkerOf("9774d56d682e549c"))
    }

    @Test
    fun `GIVEN a device id EXPECT a marker other than the constant`() {
        assertFalse(ReinstallRepository.CONSTANT_MARKER.contentEquals(ReinstallRepository.deviceMarkerOf("device")))
    }

    // endregion

    private companion object {
        val THIS_DEVICE = ReinstallRepository.deviceMarkerOf("this-device")
        val OTHER_DEVICE = ReinstallRepository.deviceMarkerOf("other-device")
    }
}
