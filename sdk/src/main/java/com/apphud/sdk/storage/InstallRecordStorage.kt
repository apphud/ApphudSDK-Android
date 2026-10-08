package com.apphud.sdk.storage

import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Properties

internal enum class InstallState {
    /** A new install whose reinstall marker hasn't been read yet. */
    PENDING,
    FIRST_INSTALL,
    REINSTALL,

    /** The install predates this SDK version: it isn't a new install, so it is neither. */
    EXISTING,
}

/** How this install was classified, and whether the backend has its reinstall flag. */
internal data class InstallRecord(
    val state: InstallState,
    val reinstallSent: Boolean = false,
)

/**
 * Per-install state of the reinstall check. Install-scoped: survives logout.
 */
internal interface InstallRecordStorage {
    /** Null when nothing has been saved yet. Throws when the saved record can't be read. */
    fun read(): InstallRecord?

    /** Throws when the record wasn't saved. */
    fun write(record: InstallRecord)
}

/**
 * Keeps the record in `noBackupFilesDir`: uninstall removes it and Auto Backup never restores it,
 * so a missing record means a new install even when the backed-up preferences come back.
 */
internal class FileInstallRecordStorage(
    private val directory: () -> File?,
) : InstallRecordStorage {

    override fun read(): InstallRecord? {
        val file = atomicFile() ?: return null
        val properties = Properties()
        try {
            file.openRead().use { properties.load(it) }
        } catch (e: FileNotFoundException) {
            // Thrown for any open failure: only a file that isn't there means nothing was saved.
            if (file.baseFile.exists()) throw e
            return null
        } catch (e: IllegalArgumentException) {
            return InstallRecord(InstallState.EXISTING)
        }
        val state = properties.getProperty(STATE_KEY)
            ?.let { name -> runCatching { InstallState.valueOf(name) }.getOrNull() }
            // The record existed, so this install was classified before: never reclassify it.
            ?: return InstallRecord(InstallState.EXISTING)
        return InstallRecord(state, reinstallSent = properties.getProperty(REINSTALL_SENT_KEY).toBoolean())
    }

    override fun write(record: InstallRecord) {
        val file = atomicFile() ?: throw IOException("No directory for the install record")
        val properties = Properties().apply {
            setProperty(STATE_KEY, record.state.name)
            setProperty(REINSTALL_SENT_KEY, record.reinstallSent.toString())
        }
        val out = file.startWrite()
        try {
            properties.store(out, null)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
        // AtomicFile only logs a failed rename.
        if (read() != record) throw IOException("Install record not saved")
    }

    private fun atomicFile(): AtomicFile? = directory()?.let { AtomicFile(File(it, FILE_NAME)) }

    companion object {
        const val FILE_NAME = "apphud_install_record"
        private const val STATE_KEY = "state"
        private const val REINSTALL_SENT_KEY = "reinstall_sent"
    }
}
