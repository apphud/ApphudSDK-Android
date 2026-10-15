package com.apphud.sdk.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FileInstallRecordStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `GIVEN saved record EXPECT same record read back`() {
        val storage = FileInstallRecordStorage { folder.root }
        val record = InstallRecord(InstallState.REINSTALL, installationId = "installation-1")

        storage.write(record)

        assertEquals(record, storage.read())
    }

    @Test
    fun `GIVEN two changes of the record at once EXPECT neither lost`() {
        // The reinstall check's verdict and identity dropping the old device id, at the same time.
        val storage = FileInstallRecordStorage { folder.root }
        storage.write(InstallRecord(InstallState.PENDING, "installation-1", legacyDeviceId = "OLD"))
        val inVerdict = CountDownLatch(1)
        val release = CountDownLatch(1)
        val verdict = thread {
            storage.update(fallback = null) {
                inVerdict.countDown()
                release.await(5, TimeUnit.SECONDS)
                it.copy(state = InstallState.FIRST_INSTALL)
            }
        }
        assertTrue(inVerdict.await(5, TimeUnit.SECONDS))
        val drop = thread { storage.update(fallback = null) { it.copy(legacyDeviceId = null) } }
        // Without one lock the drop would save here and the verdict would write it back.
        Thread.sleep(200)
        release.countDown()
        verdict.join(5_000)
        drop.join(5_000)

        assertEquals(InstallRecord(InstallState.FIRST_INSTALL, "installation-1", legacyDeviceId = null), storage.read())
    }

    @Test
    fun `GIVEN saved record with an old device id EXPECT it read back`() {
        val storage = FileInstallRecordStorage { folder.root }
        val record = InstallRecord(InstallState.EXISTING, "OLD", legacyDeviceId = "OLD")

        storage.write(record)

        assertEquals(record, storage.read())
    }

    @Test
    fun `GIVEN saved record with the app's reinstall answer EXPECT it read back`() {
        val storage = FileInstallRecordStorage { folder.root }

        listOf(true, false, null).forEach { answer ->
            val record = InstallRecord(InstallState.EXISTING, "installation-1", hostReinstall = answer)
            storage.write(record)
            assertEquals(record, storage.read())
        }
    }

    @Test
    fun `GIVEN saved record with the reinstall reported EXPECT the flag read back`() {
        val storage = FileInstallRecordStorage { folder.root }
        val record = InstallRecord(InstallState.REINSTALL, "installation-1", reinstallReported = true)

        storage.write(record)

        assertEquals(record, storage.read())
    }

    @Test
    fun `GIVEN saved record with a restored person EXPECT the restored ids read back`() {
        val storage = FileInstallRecordStorage { folder.root }
        val record = InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = "A", restoredUserId = "U")

        storage.write(record)

        assertEquals(record, storage.read())
    }
}
