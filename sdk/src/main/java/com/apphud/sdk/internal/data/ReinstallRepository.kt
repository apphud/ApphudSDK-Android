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
 * - The first launch of a new install reads the marker: this device's marker means the app was
 *   installed here before. The result is saved for the install, so every relaunch and retry
 *   reports the same value, and logout keeps it.
 * - A failed or slow read leaves the result unknown and is retried in the background on later
 *   launches. The result is saved before the marker is written, so a later read never takes the
 *   install's own marker for a previous one.
 * - The marker holds a hash of this device's id: a marker copied to a new phone by a device-to-device
 *   transfer doesn't match there, so a new phone is not a reinstall.
 * - Every launch puts this device's marker back if it is missing, e.g. after the host app cleared
 *   its Block Store data.
 * - An install that predates this SDK version isn't a new install: its result stays unknown.
 * - Opted out of tracking ([deviceMarker] is null): no marker is read or written.
 */
internal class ReinstallRepository(
    private val storage: InstallRecordStorage,
    private val markerSource: InstallMarkerSource,
    private val deviceMarker: () -> ByteArray?,
    private val isFreshPackageInstall: () -> Boolean?,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val decided = CompletableDeferred<Unit>()

    @Volatile
    private var record: InstallRecord? = null

    // One check per process: later callers wait for it instead of reading the marker again.
    private val check: Job by lazy {
        scope.launch {
            runCatchingCancellable { checkInstall() }
                .onFailure { ApphudLog.logE("Reinstall check failed: ${it.message}") }
            decided.complete(Unit)
        }
    }

    fun start() {
        check.start()
    }

    /**
     * True on a reinstall, false on a first install, null while unknown. Reads memory only: null
     * until [start] has loaded this install's result; [awaitIsReinstall] waits for it.
     */
    fun isReinstall(): Boolean? =
        when (record?.state) {
            InstallState.REINSTALL -> true
            InstallState.FIRST_INSTALL -> false
            else -> null
        }

    /** [isReinstall] once this launch's check has decided, waiting at most [TIMEOUT_MS]. */
    suspend fun awaitIsReinstall(): Boolean? {
        start()
        withTimeoutOrNull(TIMEOUT_MS) { decided.await() }
        return isReinstall()
    }

    /** True until a registration that carried it succeeds, otherwise null. */
    fun pendingReinstallFlag(): Boolean? =
        record?.takeIf { it.state == InstallState.REINSTALL && !it.reinstallSent }?.let { true }

    /** Clears the flag at once, so a registration queued behind this one doesn't send it again. */
    fun onReinstallSent() {
        val next = synchronized(lock) {
            record?.takeIf { !it.reinstallSent }?.copy(reinstallSent = true)?.also { record = it }
        } ?: return
        scope.launch {
            runCatching { storage.write(next) }
                .onFailure { ApphudLog.logE("Reinstall flag not saved as sent: ${it.message}") }
        }
    }

    private suspend fun checkInstall() {
        val loaded = load()
        val current = loaded ?: classify() ?: return
        // Only the launch that created the record holds registration for the read; later launches
        // retry a failed read in the background.
        if (current.state != InstallState.PENDING || loaded != null) decided.complete(Unit)

        val marker = deviceMarker() ?: return
        val stored = bounded { markerSource.read() }.getOrElse {
            ApphudLog.log("Install marker not read: ${it.message}")
            return
        }
        val matches = stored?.contentEquals(marker) == true
        if (current.state == InstallState.PENDING) {
            val state = if (matches) InstallState.REINSTALL else InstallState.FIRST_INSTALL
            update { it.copy(state = state) }
            decided.complete(Unit)
            ApphudLog.log("Install classified: $state")
        }
        if (!matches) {
            bounded { markerSource.write(marker) }
                .onFailure { ApphudLog.log("Install marker not written: ${it.message}") }
        }
    }

    private suspend fun <T> bounded(block: suspend () -> T): Result<T> =
        withTimeoutOrNull(TIMEOUT_MS) { runCatchingCancellable { block() } }
            ?: Result.failure(TimeoutException("no answer in $TIMEOUT_MS ms"))

    private fun load(): InstallRecord? =
        synchronized(lock) { record ?: storage.read().also { record = it } }

    /** Null when the package info is unavailable: nothing is saved, and the next launch tries again. */
    private fun classify(): InstallRecord? {
        val fresh = isFreshPackageInstall() ?: return null
        return synchronized(lock) {
            record ?: InstallRecord(if (fresh) InstallState.PENDING else InstallState.EXISTING).also(::save)
        }
    }

    private fun update(transform: (InstallRecord) -> InstallRecord) {
        synchronized(lock) {
            val current = record ?: return
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

        /** SHA-256 of the device's ANDROID_ID, or null when there is none. */
        fun deviceMarkerOf(androidId: String?): ByteArray? =
            androidId?.takeUnless { it.isBlank() }
                ?.let { MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8)) }
    }
}
