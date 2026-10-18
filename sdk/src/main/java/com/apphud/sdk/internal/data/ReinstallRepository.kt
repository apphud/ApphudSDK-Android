package com.apphud.sdk.internal.data

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.storage.InstallRecord
import com.apphud.sdk.storage.InstallRecordStorage
import com.apphud.sdk.storage.InstallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * - A reinstall is reported until a registration carrying it succeeds, then never again for this
 *   install: a later customer of the same install (after logout, a switch) is not a reinstall.
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
    @Volatile
    private var decided = CompletableDeferred<Unit>()

    @Volatile
    private var record: InstallRecord? = null

    @Volatile
    private var hostDecides = false

    @Volatile
    private var hostValue: Boolean? = null
    @Volatile
    private var installationId: String? = null

    // A new install's first launch, or one with a restored person to confirm, holds registration
    // for the marker read.
    @Volatile
    private var holdRegistrationForRead = false

    /** The old SDK's `device_id` of a record this launch couldn't save. */
    @Volatile
    private var unsavedLegacyDeviceId: String? = null

    // What this process knows even when a save failed: a later reload of the record never undoes it.
    @Volatile
    private var reinstallReportedInProcess = false

    @Volatile
    private var legacyDeviceIdDroppedInProcess = false

    // A restored person waits for this launch's read, until it is dropped or kept. Guarded by [lock].
    private var restoredPersonOpen = false

    // Reused until host mode supersedes it; returning to standalone needs a new native verdict.
    private var check: Job? = null
    @Volatile
    private var checkGeneration = 0L

    /**
     * [newInstall]: this launch created the install record, so its first registration waits for the
     * read. [restoredPerson]: the ids came back with restored data, and the read decides whether they
     * stay, so registration waits too.
     */
    fun start(
        newInstall: Boolean = false,
        restoredPerson: Boolean = false,
        unsavedLegacyDeviceId: String? = null,
        installationId: String? = null,
    ) {
        val job = synchronized(lock) {
            this.unsavedLegacyDeviceId = unsavedLegacyDeviceId
            if (hostDecides) record = null
            selectInstallation(installationId ?: load()?.installationId)
            hostDecides = false
            hostValue = null
            if (newInstall || restoredPerson) holdRegistrationForRead = true
            if (restoredPerson) restoredPersonOpen = true
            check ?: run {
                val verdict = CompletableDeferred<Unit>().also { decided = it }
                val generation = ++checkGeneration
                scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        runCatchingCancellable { checkInstall(generation, verdict) }
                            .onFailure { ApphudLog.logE("Reinstall check failed: ${it.message}") }
                    } finally {
                        verdict.complete(Unit)
                    }
                }.also { check = it }
            }
        }
        resolved.complete(Unit)
        job.start()
    }

    /** The app supplies the ids and the flag: the SDK runs no check of its own. */
    fun useHostValue(isReinstall: Boolean?, installationId: String? = null) {
        prepareForHostIds()
        synchronized(lock) {
            val previousId = this.installationId ?: load()?.installationId
            // Identity has just attempted to write this start's record.
            record = null
            val acceptedId = installationId ?: runCatching { storage.read() }.getOrNull()?.installationId
            val remembered = hostValue.takeIf { previousId == acceptedId }
            selectInstallation(acceptedId)
            hostValue = isReinstall ?: remembered ?: load()?.hostReinstall
            hostDecides = true
            hostValue?.let(::keepHostValue)
            // An earlier ACK may not have reached disk. Flush it only to this installation.
            if (reinstallReportedInProcess) {
                runCatching { update { it.copy(reinstallReported = true) } }
                    .onFailure { ApphudLog.logE("Registration not saved as reported: ${it.message}") }
            }
        }
        resolved.complete(Unit)
        // A reinstall not reported yet goes with the next registration, which isn't skipped then.
        if (reinstallFlag() == true) onDecided()
    }

    /** Supersede an in-flight native verdict before the host's identity record is written. */
    fun prepareForHostIds() {
        synchronized(lock) {
            hostDecides = true
            restoredPersonOpen = false
            holdRegistrationForRead = false
            checkGeneration += 1
            check?.cancel()
            check = null
            decided.complete(Unit)
        }
    }

    /**
     * The app's reinstall answer that arrived after start: the next registration, on a later launch at
     * the latest, carries a reinstall until one carrying it succeeds. False when the SDK runs its own
     * check: the answer is ignored then.
     */
    fun useLaterHostValue(isReinstall: Boolean): Boolean {
        if (!hostDecides) return false
        hostValue = isReinstall
        keepHostValue(isReinstall)
        if (reinstallFlag() == true) onDecided()
        return true
    }

    /** Kept for later launches: a later start may come before the app has its answer again. */
    private fun keepHostValue(isReinstall: Boolean) {
        if (load()?.hostReinstall == isReinstall) return
        // Not saved: still used in this launch.
        runCatching { update { it.copy(hostReinstall = isReinstall) } }
            .onFailure { ApphudLog.logE("Reinstall answer not saved: ${it.message}") }
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

    /**
     * `reinstall: true` goes on a reinstalled install's registrations until one of them succeeds;
     * otherwise no param. The backend records it on the customer that registration resolves to.
     */
    fun reinstallFlag(): Boolean? {
        val reported = load()?.reinstallReported == true
        return if (isReinstall() == true && !reinstallReportedInProcess && !reported) true else null
    }

    /**
     * The old SDK's `device_id`, sent as `legacy_device_id` until a registration carrying it succeeds;
     * not after a logout or a user switch ([forgetLegacyDeviceId]).
     */
    fun legacyDeviceId(): String? =
        if (legacyDeviceIdDroppedInProcess) null else load().let { if (it != null) it.legacyDeviceId else unsavedLegacyDeviceId }

    /** A logout or a user switch before a registration adopted the old customer: it is the person before's. */
    fun forgetLegacyDeviceId() {
        legacyDeviceIdDroppedInProcess = true
        unsavedLegacyDeviceId = null
        synchronized(lock) {
            // The record in memory, so a failed save still forgets it in this process.
            load()
            // From disk, so a forget that wasn't saved before is saved now.
            runCatching { update { it.copy(legacyDeviceId = null) } }
                .onFailure {
                    record = record?.copy(legacyDeviceId = null)
                    ApphudLog.logE("Old device id not saved as forgotten: ${it.message}")
                }
        }
    }

    /**
     * A registration succeeded. One that carried `reinstall: true` reported the reinstall, and one that
     * carried `legacy_device_id` had the old customer adopted: later ones send neither again.
     */
    fun onRegistered(reinstallSent: Boolean, legacyDeviceIdSent: Boolean, installationIdSent: String? = null): Unit = synchronized(lock) {
        if (installationIdSent != null && installationIdSent != installationId) return@synchronized
        if (!reinstallSent && !legacyDeviceIdSent) return@synchronized
        if (reinstallSent) reinstallReportedInProcess = true
        if (legacyDeviceIdSent) {
            legacyDeviceIdDroppedInProcess = true
            unsavedLegacyDeviceId = null
        }
        val reported = { it: InstallRecord ->
            it.copy(
                reinstallReported = it.reinstallReported || reinstallSent,
                legacyDeviceId = it.legacyDeviceId.takeUnless { legacyDeviceIdSent },
            )
        }
        synchronized(lock) {
            runCatching { update(reported) }
                .onFailure {
                    // Not sent again in this process at least.
                    record = record?.let(reported)
                    ApphudLog.logE("Registration not saved as reported: ${it.message}")
                }
        }
    }

    private fun isCurrentCheck(generation: Long): Boolean = !hostDecides && generation == checkGeneration

    private suspend fun checkInstall(generation: Long, verdict: CompletableDeferred<Unit>) {
        if (!isCurrentCheck(generation)) return
        val current = load() ?: return
        // The launch that created the record, or one with a restored person to confirm, holds
        // registration for the read; later launches retry a failed read in the background.
        if (current.state != InstallState.PENDING || !holdRegistrationForRead) verdict.complete(Unit)

        val marker = deviceMarker()
        val stored = bounded { markerSource.read() }.getOrElse {
            ApphudLog.log("Install marker not read: ${it.message}")
            synchronized(lock) { if (isCurrentCheck(generation)) keepRestoredPerson() }
            return
        }
        val matches = stored?.contentEquals(marker) == true
        if (!isCurrentCheck(generation)) return
        if (current.state == InstallState.PENDING) {
            val state = if (matches) InstallState.REINSTALL else InstallState.FIRST_INSTALL
            // Before registration goes on: a reinstall never registers with the restored person. Under
            // the lock, so a registration whose wait ends meanwhile waits for the drop to finish.
            synchronized(lock) {
                if (!isCurrentCheck(generation)) return
                if (closeRestoredPerson() && matches) dropRestoredPerson()
                try {
                    // A reinstall on this device: the old customer is the deleted install's, nothing to adopt.
                    update {
                        it.copy(state = state, legacyDeviceId = it.legacyDeviceId.takeUnless { matches }).personSettled()
                    }
                } finally {
                    // Not even when the record wasn't saved.
                    if (matches) record = record?.copy(legacyDeviceId = null)
                }
            }
            synchronized(lock) { if (isCurrentCheck(generation)) onDecided() }
            verdict.complete(Unit)
            ApphudLog.log("Install classified: $state")
        }
        if (!matches && isCurrentCheck(generation)) {
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

    /** In-process answers and ACKs belong to one installation, even if its record couldn't be saved. */
    private fun selectInstallation(id: String?) {
        if (installationId != id) reinstallReportedInProcess = false
        installationId = id
        record = record?.takeIf(::belongsToInstallation)
    }

    private fun belongsToInstallation(value: InstallRecord): Boolean =
        installationId == null || value.installationId == installationId

    private fun load(): InstallRecord? = synchronized(lock) {
        record?.takeIf(::belongsToInstallation)
            ?: runCatching { storage.read() }.getOrNull()?.takeIf(::belongsToInstallation).also { record = it }
    }

    private fun update(transform: (InstallRecord) -> InstallRecord) {
        synchronized(lock) {
            // The storage lock also excludes an identity write between the key check and the change.
            val acceptedId = installationId
            record = storage.update(fallback = record) {
                if (acceptedId == null || it.installationId == acceptedId) transform(it) else it
            }?.takeIf(::belongsToInstallation)
        }
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
