package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import com.apphud.sdk.storage.InstallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.concurrent.TimeoutException

/**
 * Tells a reinstall from a first install with a marker that outlives uninstall.
 *
 * - [IdentityStore] creates the install record at start; a new install's record is pending.
 * - The first launch of a new install reads the marker: this device's marker means the app was
 *   installed here before. The result is saved for the install, so every relaunch and retry
 *   reports the same value, and logout keeps it.
 * - A failed or slow read leaves the result unknown and is retried in the background on later
 *   launches. The result is saved before the marker is written, so a later read never takes the
 *   install's own marker for a previous one.
 * - The marker holds a hash of this device's id: a marker copied to a new phone by a device-to-device
 *   transfer doesn't match there, so a new phone is not a reinstall. Without the device id the
 *   marker is a constant, and such a transfer reads as a reinstall.
 * - Every launch puts this device's marker back if it is missing, e.g. after the host app cleared
 *   its Block Store data.
 * - An install updated in place from an older SDK version isn't a new install: its result stays unknown.
 * - When the app supplies the ids, it decides the flag ([useHostValue]): no marker is read or written.
 * - A person Auto Backup restored onto a new install waits for this check, and registration waits
 *   with it: this device's marker means a reinstall, and the person is dropped before registration
 *   goes on. No marker, another device's, or no answer keeps the person, for good.
 */
internal class ReinstallRepository(
    private val storage: InstallRecordStorage,
    private val markerSource: InstallMarkerSource,
    private val deviceMarker: () -> ByteArray,
    private val scope: CoroutineScope,
    /** Called when a new install's result is decided, so the next registration reports it. */
    private val onDecided: () -> Unit = {},
    /** Drops the person Auto Backup restored: this device's marker shows a reinstall. */
    private val dropRestoredPerson: () -> Unit = {},
    /** How long a read, a write and a wait for the verdict may take. */
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    private val lock = Any()

    /** Completed once start resolved this launch: [start] or [useHostValue]. */
    private val resolved = CompletableDeferred<Unit>()
    /** Completed only by the check: the SDK's own result is known, or the read failed or timed out. */
    private val decided = CompletableDeferred<Unit>()

    @Volatile
    private var record: InstallRecord? = null

    @Volatile
    private var hostDecides = false

    @Volatile
    private var hostValue: Boolean? = null

    // A new install's first launch, or one with a restored person to confirm, holds registration
    // for the marker read.
    @Volatile
    private var holdRegistrationForRead = false

    // A restored person waits for this launch's read, until it is dropped or kept. Guarded by [lock].
    private var restoredPersonOpen = false

    // One check per process: later callers wait for it instead of reading the marker again.
    private val check: Job by lazy {
        scope.launch {
            runCatchingCancellable { checkInstall() }
                .onFailure { ApphudLog.logE("Reinstall check failed: ${it.message}") }
            decided.complete(Unit)
        }
    }

    /**
     * [newInstall]: this launch created the install record, so its first registration waits for the
     * read. [restoredPerson]: the ids came back with restored data, and the read decides whether they
     * stay, so registration waits too.
     */
    fun start(newInstall: Boolean = false, restoredPerson: Boolean = false) {
        // A later start without supplied ids (after logout) goes back to the SDK's own check.
        hostDecides = false
        hostValue = null
        if (newInstall || restoredPerson) holdRegistrationForRead = true
        if (restoredPerson) synchronized(lock) { restoredPersonOpen = true }
        resolved.complete(Unit)
        check.start()
    }

    /** The app supplies the ids and the flag: the SDK runs no check of its own. */
    fun useHostValue(isReinstall: Boolean?) {
        hostValue = isReinstall
        hostDecides = true
        resolved.complete(Unit)
    }

    /**
     * True on a reinstall, false on a first install, null while unknown. Reads memory only: null
     * until [start] has loaded this install's result; [awaitIsReinstall] waits for it.
     */
    fun isReinstall(): Boolean? {
        if (hostDecides) return hostValue
        return when (record?.state) {
            InstallState.REINSTALL -> true
            InstallState.FIRST_INSTALL -> false
            else -> null
        }
    }

    /**
     * [isReinstall] once this launch's check has decided, waiting at most [timeoutMs]. Never starts
     * the check itself: called before `Apphud.start`, it waits for start instead of reading the
     * marker before the install record exists.
     */
    suspend fun awaitIsReinstall(): Boolean? {
        withTimeoutOrNull(timeoutMs) { awaitVerdict() }
        return isReinstall()
    }

    /**
     * [awaitIsReinstall] for a registration: when it goes on without a verdict, it carries the
     * restored person, which then stays.
     */
    suspend fun awaitBeforeRegistration(): Boolean? {
        if (withTimeoutOrNull(timeoutMs) { awaitVerdict() } == null) keepRestoredPerson()
        return isReinstall()
    }

    private suspend fun awaitVerdict() {
        resolved.await()
        // The app's own flag needs no wait.
        if (!hostDecides) decided.await()
    }

    /** `reinstall: true` goes on every registration of a reinstalled install; otherwise no param. */
    fun reinstallFlag(): Boolean? = if (isReinstall() == true) true else null

    private suspend fun checkInstall() {
        val current = load() ?: return
        // The launch that created the record, or one with a restored person to confirm, holds
        // registration for the read; later launches retry a failed read in the background.
        if (current.state != InstallState.PENDING || !holdRegistrationForRead) decided.complete(Unit)

        val marker = deviceMarker()
        val stored = bounded { markerSource.read() }.getOrElse {
            ApphudLog.log("Install marker not read: ${it.message}")
            keepRestoredPerson()
            return
        }
        val matches = stored?.contentEquals(marker) == true
        if (current.state == InstallState.PENDING) {
            val state = if (matches) InstallState.REINSTALL else InstallState.FIRST_INSTALL
            // Before registration goes on: a reinstall never registers with the restored person. Under
            // the lock, so a registration whose wait ends meanwhile waits for the drop to finish.
            synchronized(lock) { if (closeRestoredPerson() && matches) dropRestoredPerson() }
            update { it.copy(state = state).personSettled() }
            decided.complete(Unit)
            onDecided()
            ApphudLog.log("Install classified: $state")
        }
        if (!matches) {
            bounded { markerSource.write(marker) }
                .onFailure { ApphudLog.log("Install marker not written: ${it.message}") }
        }
    }

    /** Ends the wait on the restored person: true only for the caller that ended it. */
    private fun closeRestoredPerson(): Boolean =
        synchronized(lock) { restoredPersonOpen.also { restoredPersonOpen = false } }

    /** No verdict, or none in time: the restored person stays, as on a new device, for good. */
    private fun keepRestoredPerson() {
        if (!closeRestoredPerson()) return
        runCatching { update { it.personSettled() } }
            .onFailure { ApphudLog.logE("Restored person not saved as kept: ${it.message}") }
    }

    private suspend fun <T> bounded(block: suspend () -> T): Result<T> =
        withTimeoutOrNull(timeoutMs) { runCatchingCancellable { block() } }
            ?: Result.failure(TimeoutException("no answer in $timeoutMs ms"))

    private fun load(): InstallRecord? =
        synchronized(lock) { record ?: runCatching { storage.read() }.getOrNull().also { record = it } }

    private fun update(transform: (InstallRecord) -> InstallRecord) {
        synchronized(lock) {
            // From disk: identity resolution may have rewritten the record since it was loaded.
            val current = runCatching { storage.read() }.getOrNull() ?: record ?: return
            val next = transform(current)
            if (next != current) save(next)
        }
    }

    /** Throws when the record wasn't saved, so nothing acts on a state that a relaunch wouldn't see. */
    private fun save(next: InstallRecord) {
        storage.write(next)
        record = next
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L

        /** Some old devices report this same ANDROID_ID: it tells no device apart. */
        private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

        /** The marker when the device id is unavailable or must not be read. */
        val CONSTANT_MARKER: ByteArray = sha256("apphud-install-marker")

        /** SHA-256 of the device's ANDROID_ID, or [CONSTANT_MARKER] when there is none. */
        fun deviceMarkerOf(androidId: String?): ByteArray =
            androidId?.takeUnless { it.isBlank() || it == BROKEN_ANDROID_ID }?.let(::sha256) ?: CONSTANT_MARKER

        private fun sha256(value: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    }
}
