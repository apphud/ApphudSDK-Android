package com.apphud.sdk.internal.data

import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import com.apphud.sdk.storage.InstallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
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
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.Executor

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
    private var drops = 0
    private var verdictSeenAtDrop: Boolean? = null

    /** A launch over the same storage. A new install's record is created first, as identity resolution does. */
    private fun CoroutineScope.launch(
        state: InstallState? = null,
        start: Boolean = true,
        restoredPerson: Boolean = false,
    ): ReinstallRepository {
        val newInstall = storage.record == null
        if (newInstall) {
            storage.write(InstallRecord(state ?: InstallState.PENDING, "installation-1", restoredAnonymousId = RESTORED.takeIf { restoredPerson }))
        }
        lateinit var repository: ReinstallRepository
        repository = ReinstallRepository(
            storage = storage,
            markerSource = markers,
            deviceMarker = { THIS_DEVICE },
            scope = this,
            onDecided = { decidedCalls += 1 },
            dropRestoredPerson = {
                drops += 1
                verdictSeenAtDrop = repository.isReinstall()
            },
        )
        return repository.also { if (start) it.start(newInstall, restoredPerson) }
    }

    /** A new install with a restored person whose check never runs: only the waits act. */
    private fun idleLaunch(): ReinstallRepository {
        storage.write(InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = RESTORED))
        val neverRuns = CoroutineScope(Executor { }.asCoroutineDispatcher())
        return ReinstallRepository(storage, markers, { THIS_DEVICE }, neverRuns, dropRestoredPerson = { drops += 1 })
            .also { it.start(newInstall = true, restoredPerson = true) }
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

    // region person restored by Auto Backup

    @Test
    fun `GIVEN restored person and this device's marker EXPECT the person dropped`() = runTest {
        markers.stored = THIS_DEVICE

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertEquals(1, drops)
    }

    @Test
    fun `GIVEN restored person and this device's marker EXPECT the drop before the verdict is out`() = runTest {
        markers.stored = THIS_DEVICE

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertNull(verdictSeenAtDrop)
    }

    @Test
    fun `GIVEN restored person and this device's marker EXPECT the person settled in the record`() = runTest {
        markers.stored = THIS_DEVICE

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertEquals(InstallState.REINSTALL to false, storage.record?.state to storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored person and no marker EXPECT the person kept`() = runTest {
        launch(restoredPerson = true).awaitBeforeRegistration()
        advanceUntilIdle()

        assertEquals(0 to false, drops to storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored person and another device's marker EXPECT the person kept`() = runTest {
        markers.stored = OTHER_DEVICE

        launch(restoredPerson = true).awaitBeforeRegistration()
        advanceUntilIdle()

        assertEquals(0 to false, drops to storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored person and a failed read EXPECT the person kept`() = runTest {
        markers.readError = IllegalStateException("Block Store failed")

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertEquals(0 to false, drops to storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored person kept after a failed read EXPECT a later read never drops it`() = runTest {
        markers.readError = IllegalStateException("Block Store failed")
        launch(restoredPerson = true).awaitBeforeRegistration()
        advanceUntilIdle()
        markers.readError = null
        markers.stored = THIS_DEVICE.copyOf()

        launch().awaitBeforeRegistration()
        advanceUntilIdle()

        assertEquals(0, drops)
    }

    @Test
    fun `GIVEN restored person and a read that never answers EXPECT the person kept after the bound`() = runTest {
        markers.neverAnswers = true

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertEquals(0 to false, drops to storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN nothing restored and this device's marker EXPECT nothing dropped`() = runTest {
        markers.stored = THIS_DEVICE

        launch().awaitBeforeRegistration()

        assertEquals(0, drops)
    }

    @Test
    fun `GIVEN restored person left undecided by an earlier launch EXPECT registration held for the read`() = runTest {
        launch(start = false, restoredPerson = true)
        markers.neverAnswers = true

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertEquals(5_000L, currentTime)
    }

    @Test
    fun `GIVEN restored person left undecided by an earlier launch EXPECT this device's marker drops it`() = runTest {
        launch(start = false, restoredPerson = true)
        markers.stored = THIS_DEVICE

        launch(restoredPerson = true).awaitBeforeRegistration()

        assertEquals(1, drops)
    }

    @Test
    fun `GIVEN restored person and registration going on without a verdict EXPECT the person kept for good`() = runTest {
        val repository = idleLaunch()

        repository.awaitBeforeRegistration()

        assertEquals(false, storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN restored person and the app's own wait timing out EXPECT the read still decides the person`() = runTest {
        val repository = idleLaunch()

        repository.awaitIsReinstall()

        assertEquals(true, storage.record?.restoredPerson)
    }

    @Test
    fun `GIVEN registration's wait ends while the drop runs EXPECT registration goes on after the drop`() {
        storage.write(InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = RESTORED))
        markers.stored = THIS_DEVICE
        val dropping = CountDownLatch(1)
        val release = CountDownLatch(1)
        val dropDone = AtomicBoolean(false)
        val repository = ReinstallRepository(
            storage = storage,
            markerSource = markers,
            deviceMarker = { THIS_DEVICE },
            scope = CoroutineScope(Dispatchers.Default),
            dropRestoredPerson = {
                dropping.countDown()
                release.await()
                dropDone.set(true)
            },
            timeoutMs = 50,
        )
        repository.start(newInstall = true, restoredPerson = true)
        assertTrue("the drop never started", dropping.await(5, TimeUnit.SECONDS))
        val dropDoneWhenRegistrationWentOn = AtomicBoolean(false)
        val registration = thread {
            runBlocking { repository.awaitBeforeRegistration() }
            dropDoneWhenRegistrationWentOn.set(dropDone.get())
        }

        Thread.sleep(300)
        release.countDown()
        registration.join()

        assertTrue(dropDoneWhenRegistrationWentOn.get())
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
        const val RESTORED = "restored-anonymous-id"
    }
}
