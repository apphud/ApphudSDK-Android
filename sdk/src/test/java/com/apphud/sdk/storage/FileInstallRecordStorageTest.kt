package com.apphud.sdk.storage

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
    fun `GIVEN saved record with a restored person EXPECT the restored ids read back`() {
        val storage = FileInstallRecordStorage { folder.root }
        val record = InstallRecord(InstallState.PENDING, "installation-1", restoredAnonymousId = "A", restoredUserId = "U")

        storage.write(record)

        assertEquals(record, storage.read())
    }
}
