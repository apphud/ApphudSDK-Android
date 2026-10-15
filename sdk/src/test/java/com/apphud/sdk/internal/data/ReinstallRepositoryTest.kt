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
        var failWrites = false
        override fun read(): InstallRecord? = record
        override fun write(record: InstallRecord) {
            if (failWrites) throw java.io.IOException("not saved")
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
        legacyDeviceId: String? = null,
    ): ReinstallRepository {
        val newInstall = storage.record == null
        if (newInstall) {
            storage.write(
                InstallRecord(
                    state ?: InstallState.PENDING,
                    "installation-1",
                    restoredAnonymousId = RESTORED.takeIf { restoredPerson },
                    legacyDeviceId = legacyDeviceId,
                ),
            )
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
    fun `GIVEN reinstall not reported yet EXPECT flag on registrations after relaunch`() = runTest {
        markers.stored = THIS_DEVICE
        launch().awaitIsReinstall()
        advanceUntilIdle()

        val relaunch = launch()
        relaunch.awaitIsReinstall()
        assertEquals(true, relaunch.reinstallFlag())
    }

    @Test
    fun `GIVEN reinstall reported EXPECT no flag`() = runTest {
        markers.stored = THIS_DEVICE
        val repository = launch()
        repository.awaitIsReinstall()

        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)

        assertNull(repository.reinstallFlag())
    }

    @Test
    fun `GIVEN reinstall reported EXPECT no flag after relaunch`() = runTest {
        markers.stored = THIS_DEVICE
        launch().also {
            it.awaitIsReinstall()
            it.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)
        }
        advanceUntilIdle()

        val relaunch = launch()
        relaunch.awaitIsReinstall()

        assertNull(relaunch.reinstallFlag())
    }

    @Test
    fun `GIVEN reinstall reported EXPECT still a reinstall`() = runTest {
        markers.stored = THIS_DEVICE
        val repository = launch()
        repository.awaitIsReinstall()

        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)

        assertEquals(true, repository.isReinstall())
    }

    @Test
    fun `GIVEN the app's reinstall flag reported EXPECT no flag`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(true)

        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)

        assertNull(repository.reinstallFlag())
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

    @Test
    fun `GIVEN the app's answer after start WHEN a reinstall EXPECT the next registration carries it and isn't skipped`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(null)
        assertNull(repository.reinstallFlag())

        assertTrue(repository.useLaterHostValue(true))

        assertEquals(true, repository.isReinstall())
        assertEquals(true, repository.reinstallFlag())
        assertEquals("the next registration isn't skipped, on a later launch either", 1, decidedCalls)
    }

    @Test
    fun `GIVEN the app's answer after start WHEN not a reinstall EXPECT nothing sent`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(null)

        repository.useLaterHostValue(false)

        assertEquals(false, repository.isReinstall())
        assertNull(repository.reinstallFlag())
        assertEquals(0, decidedCalls)
    }

    @Test
    fun `GIVEN the app's answer after start WHEN a later launch passes none EXPECT its registration carries it`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(null)
        repository.useLaterHostValue(true)

        val next = launch(start = false)
        decidedCalls = 0
        next.useHostValue(null)

        assertEquals(true, next.reinstallFlag())
        assertEquals("the registration of that launch isn't skipped", 1, decidedCalls)
    }

    @Test
    fun `GIVEN the app's answers EXPECT the latest kept for a later launch until one passed at start replaces it`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(null)
        repository.useLaterHostValue(true)
        repository.useLaterHostValue(false)

        val next = launch(start = false)
        next.useHostValue(null)
        assertEquals(false, next.isReinstall())
        assertNull(next.reinstallFlag())

        launch(start = false).useHostValue(true)
        val after = launch(start = false)
        after.useHostValue(null)
        assertEquals("an answer passed to start replaces the kept one", true, after.isReinstall())
    }

    @Test
    fun `GIVEN a record loaded before this start's WHEN the app passes its ids EXPECT this start's record used`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(null)
        repository.useLaterHostValue(true)
        // A logout and a start with another installation: identity writes the new record.
        storage.record = InstallRecord(InstallState.EXISTING, "installation-2")

        repository.useHostValue(null)

        assertNull(repository.isReinstall())
        assertNull(repository.reinstallFlag())
        repository.useLaterHostValue(true)
        assertEquals("kept for later launches", true, storage.record?.hostReinstall)
    }

    @Test
    fun `GIVEN the record can't be saved WHEN the app answers EXPECT no throw and the answer used`() = runTest {
        val repository = launch(start = false)
        storage.failWrites = true

        repository.useHostValue(true)
        assertEquals(true, repository.reinstallFlag())

        assertTrue(repository.useLaterHostValue(false))
        assertEquals(false, repository.isReinstall())
    }

    @Test
    fun `GIVEN two answers from the app EXPECT the latest wins`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(null)

        repository.useLaterHostValue(true)
        repository.useLaterHostValue(false)

        assertNull(repository.reinstallFlag())
    }

    @Test
    fun `GIVEN the reinstall reported WHEN the app answers again EXPECT nothing sent`() = runTest {
        val repository = launch(start = false)
        repository.useHostValue(true)
        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)
        decidedCalls = 0

        repository.useLaterHostValue(true)

        assertNull("once per install", repository.reinstallFlag())
        assertEquals(0, decidedCalls)
    }

    @Test
    fun `GIVEN the SDK runs its own check EXPECT the app's later answer ignored`() = runTest {
        val repository = launch()
        repository.awaitIsReinstall()

        assertFalse(repository.useLaterHostValue(true))

        assertEquals(false, repository.isReinstall())
    }

    @Test
    fun `GIVEN the app passes a reinstall at start EXPECT registration not skipped until one reports it`() = runTest {
        val repository = launch(start = false)

        repository.useHostValue(true)
        assertEquals(1, decidedCalls)

        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)
        repository.useHostValue(true)
        assertEquals("reported: nothing more to send", 1, decidedCalls)
    }

    // endregion

    // region old device id to adopt

    @Test
    fun `GIVEN an update from an old version EXPECT its device id offered for adoption`() = runTest {
        val repository = launch(state = InstallState.EXISTING, legacyDeviceId = "OLD")

        assertEquals("OLD", repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN a registration with the old device id succeeded EXPECT it no longer offered`() = runTest {
        val repository = launch(state = InstallState.EXISTING, legacyDeviceId = "OLD")

        repository.onRegistered(reinstallSent = false, legacyDeviceIdSent = true)

        assertNull(launch().legacyDeviceId())
    }

    @Test
    fun `GIVEN a logout or a user switch before the old customer was adopted EXPECT the old device id forgotten`() = runTest {
        val repository = launch(state = InstallState.EXISTING, legacyDeviceId = "OLD")

        repository.forgetLegacyDeviceId()

        assertNull(repository.legacyDeviceId())
        assertNull(storage.record?.legacyDeviceId)
        assertNull(launch().legacyDeviceId())
    }

    @Test
    fun `GIVEN a registration without the old device id succeeded EXPECT it still offered`() = runTest {
        val repository = launch(state = InstallState.EXISTING, legacyDeviceId = "OLD")

        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = false)

        assertEquals("OLD", repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN old data restored on this device's reinstall EXPECT no old customer to adopt`() = runTest {
        markers.stored = THIS_DEVICE

        launch(restoredPerson = true, legacyDeviceId = "OLD").awaitBeforeRegistration()

        assertNull(storage.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored on this device's reinstall and no restored person in use EXPECT no old customer to adopt`() = runTest {
        markers.stored = THIS_DEVICE

        launch(legacyDeviceId = "OLD").awaitBeforeRegistration()

        assertNull(storage.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored on a new device EXPECT the old customer still to adopt`() = runTest {
        launch(restoredPerson = true, legacyDeviceId = "OLD").awaitBeforeRegistration()
        advanceUntilIdle()

        assertEquals("OLD", storage.record?.legacyDeviceId)
    }

    @Test
    fun `GIVEN old data restored on this device's reinstall and the drop not saved EXPECT no old customer to adopt`() = runTest {
        markers.stored = THIS_DEVICE
        val repository = launch(restoredPerson = true, legacyDeviceId = "OLD", start = false)
        storage.failWrites = true

        repository.start(newInstall = true, restoredPerson = true)
        repository.awaitBeforeRegistration()

        assertEquals(1, drops)
        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN an update whose record wasn't saved EXPECT the old device id offered until a registration succeeds`() = runTest {
        val repository = ReinstallRepository(storage, markers, { THIS_DEVICE }, this)
        repository.start(unsavedLegacyDeviceId = "OLD")

        assertEquals("OLD", repository.legacyDeviceId())
        repository.onRegistered(reinstallSent = false, legacyDeviceIdSent = true)
        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN a saved record EXPECT its old device id, not an unsaved one`() = runTest {
        val repository = launch(state = InstallState.EXISTING, start = false)

        repository.start(unsavedLegacyDeviceId = "OLD")

        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN a registration's report not saved EXPECT nothing sent again in this process`() = runTest {
        markers.stored = THIS_DEVICE
        val repository = launch(legacyDeviceId = "OLD")
        repository.awaitIsReinstall()
        storage.failWrites = true

        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = true)

        assertNull(repository.reinstallFlag())
        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN a registration's report not saved WHEN a later change reloads the record EXPECT still nothing sent again`() = runTest {
        markers.stored = THIS_DEVICE
        val repository = launch(legacyDeviceId = "OLD")
        repository.awaitIsReinstall()
        storage.failWrites = true
        repository.onRegistered(reinstallSent = true, legacyDeviceIdSent = true)
        storage.failWrites = false

        // A logout: the record is read from disk again, where the report isn't.
        repository.forgetLegacyDeviceId()

        assertNull(repository.reinstallFlag())
        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN identity already dropped the old device id on disk WHEN forgotten EXPECT it no longer offered`() = runTest {
        val repository = launch(state = InstallState.EXISTING, legacyDeviceId = "OLD")
        assertEquals("OLD", repository.legacyDeviceId())
        storage.record = storage.record?.copy(legacyDeviceId = null)

        repository.forgetLegacyDeviceId()

        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN the old device id sent and its report not saved WHEN the slow check reloads the record EXPECT it not sent again`() = runTest {
        // A later launch of an install whose first read failed: registration doesn't wait for the read.
        storage.write(InstallRecord(InstallState.PENDING, "installation-1", legacyDeviceId = "OLD"))
        markers.gate = CompletableDeferred()
        val repository = launch()
        runCurrent()
        assertNull(repository.awaitBeforeRegistration())
        assertEquals("OLD", repository.legacyDeviceId())

        // The registration went on without a verdict, carrying the old device id.
        storage.failWrites = true
        repository.onRegistered(reinstallSent = false, legacyDeviceIdSent = true)
        storage.failWrites = false
        markers.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, repository.isReinstall())
        assertNull(repository.legacyDeviceId())
    }

    @Test
    fun `GIVEN a logout's forget not saved WHEN the slow check reloads the record EXPECT the old device id not offered`() = runTest {
        markers.gate = CompletableDeferred()
        val repository = launch(legacyDeviceId = "OLD")
        runCurrent()

        storage.failWrites = true
        repository.forgetLegacyDeviceId()
        storage.failWrites = false
        markers.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, repository.isReinstall())
        assertNull(repository.legacyDeviceId())
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
        storage.write(InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = RESTORED, legacyDeviceId = "OLD"))
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
        var legacyWhenRegistrationWentOn: String? = "unset"
        val registration = thread {
            runBlocking { repository.awaitBeforeRegistration() }
            dropDoneWhenRegistrationWentOn.set(dropDone.get())
            legacyWhenRegistrationWentOn = repository.legacyDeviceId()
        }

        Thread.sleep(300)
        release.countDown()
        registration.join()

        assertTrue(dropDoneWhenRegistrationWentOn.get())
        assertNull("the dropped person's old customer", legacyWhenRegistrationWentOn)
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
