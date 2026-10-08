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
        val record = InstallRecord(InstallState.REINSTALL, reinstallSent = true)

        storage.write(record)

        assertEquals(record, storage.read())
    }
}
