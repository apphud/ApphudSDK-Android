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

/** How this install was classified, and its installation id. */
internal data class InstallRecord(
    val state: InstallState,
    /** Null only in a record from before the installation id was kept here. */
    val installationId: String? = null,
    /**
     * The ids that came back with data Auto Backup restored, until the reinstall check confirms them:
     * a reinstall on this device drops them, a new device keeps them. Kept here, not in the
     * preferences, so ids set after start are never taken for restored ones.
     */
    val restoredAnonymousId: String? = null,
    val restoredUserId: String? = null,
    /** A registration that carried `reinstall: true` succeeded: the backend has this install's reinstall. */
    val reinstallReported: Boolean = false,
    /**
     * The `device_id` of the old SDK this install was updated from, or whose data was restored, sent as
     * `legacy_device_id` until a registration carrying it succeeds, not after a logout or a user switch: it
     * adopts the old customer.
     */
    val legacyDeviceId: String? = null,
    /**
     * The app's own reinstall answer when it supplies the ids, kept until it changes: an answer that came
     * after start still reaches a later launch's registration.
     */
    val hostReinstall: Boolean? = null,
) {
    val restoredPerson: Boolean get() = restoredAnonymousId != null || restoredUserId != null

    /** The reinstall check has decided about the restored person, or there was none. */
    fun personSettled(): InstallRecord = copy(restoredAnonymousId = null, restoredUserId = null)
}

/**
 * Per-install state: the installation id and the reinstall check. Install-scoped: survives logout.
 */
internal interface InstallRecordStorage {
    /** Null when nothing has been saved yet. Throws when the saved record can't be read. */
    fun read(): InstallRecord?

    /** Throws when the record wasn't saved. */
    fun write(record: InstallRecord)

    /**
     * Reads the record ([fallback] when it can't be read), changes it and saves the change, in one step
     * no other change of the record interleaves with: identity and the reinstall check both change it.
     * [transform] must not call out. Null when there is no record. Throws when the change wasn't saved.
     */
    fun update(fallback: InstallRecord?, transform: (InstallRecord) -> InstallRecord): InstallRecord? {
        val current = runCatching { read() }.getOrNull() ?: fallback ?: return null
        val next = transform(current)
        if (next != current) write(next)
        return next
    }
}

/**
 * Keeps the record in `noBackupFilesDir`: uninstall removes it and Auto Backup never restores it,
 * so a missing record means a new install even when the backed-up preferences come back.
 */
internal class FileInstallRecordStorage(
    private val directory: () -> File?,
) : InstallRecordStorage {

    @Synchronized
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
        return InstallRecord(
            state,
            installationId = properties.getProperty(INSTALLATION_ID_KEY)?.takeUnless { it.isBlank() },
            restoredAnonymousId = properties.getProperty(RESTORED_ANONYMOUS_ID_KEY)?.takeUnless { it.isBlank() },
            restoredUserId = properties.getProperty(RESTORED_USER_ID_KEY)?.takeUnless { it.isBlank() },
            reinstallReported = properties.getProperty(REINSTALL_REPORTED_KEY).toBoolean(),
            legacyDeviceId = properties.getProperty(LEGACY_DEVICE_ID_KEY)?.takeUnless { it.isBlank() },
            hostReinstall = properties.getProperty(HOST_REINSTALL_KEY)?.toBooleanStrictOrNull(),
        )
    }

    @Synchronized
    override fun write(record: InstallRecord) {
        val file = atomicFile() ?: throw IOException("No directory for the install record")
        val properties = Properties().apply {
            setProperty(STATE_KEY, record.state.name)
            record.installationId?.let { setProperty(INSTALLATION_ID_KEY, it) }
            record.restoredAnonymousId?.let { setProperty(RESTORED_ANONYMOUS_ID_KEY, it) }
            record.restoredUserId?.let { setProperty(RESTORED_USER_ID_KEY, it) }
            if (record.reinstallReported) setProperty(REINSTALL_REPORTED_KEY, "true")
            record.legacyDeviceId?.let { setProperty(LEGACY_DEVICE_ID_KEY, it) }
            record.hostReinstall?.let { setProperty(HOST_REINSTALL_KEY, it.toString()) }
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

    /** One lock for every change of the record, innermost: [transform] calls nothing that locks. */
    @Synchronized
    override fun update(fallback: InstallRecord?, transform: (InstallRecord) -> InstallRecord): InstallRecord? =
        super.update(fallback, transform)

    private fun atomicFile(): AtomicFile? = directory()?.let { AtomicFile(File(it, FILE_NAME)) }

    companion object {
        const val FILE_NAME = "apphud_install_record"
        private const val STATE_KEY = "state"
        private const val INSTALLATION_ID_KEY = "installation_id"
        private const val RESTORED_ANONYMOUS_ID_KEY = "restored_anonymous_id"
        private const val RESTORED_USER_ID_KEY = "restored_user_id"
        private const val REINSTALL_REPORTED_KEY = "reinstall_reported"
        private const val LEGACY_DEVICE_ID_KEY = "legacy_device_id"
        private const val HOST_REINSTALL_KEY = "host_reinstall"
    }
}
