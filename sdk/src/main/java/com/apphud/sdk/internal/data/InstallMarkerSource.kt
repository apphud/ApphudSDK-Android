package com.apphud.sdk.internal.data

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A marker that outlives uninstall: the Android counterpart of a Keychain item.
 */
internal interface InstallMarkerSource {
    /** The stored marker, or null when there is none. Throws when the store can't answer. */
    suspend fun read(): ByteArray?

    /** Throws when the marker wasn't stored. */
    suspend fun write(marker: ByteArray)
}

/**
 * Google Block Store. Its entries survive uninstall only while the user has Google Backup on
 * (Settings → Google → Backup), so with Backup off a reinstall finds no marker. The entry is not
 * backed up to the cloud, but a device-to-device transfer can still copy it to a new phone.
 */
internal class BlockStoreInstallMarkerSource(
    private val context: Context,
) : InstallMarkerSource {

    private val client by lazy { Blockstore.getClient(context) }

    override suspend fun read(): ByteArray? {
        checkAvailable()
        val request = RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()
        return client.retrieveBytes(request).await().blockstoreDataMap[KEY]?.bytes
    }

    override suspend fun write(marker: ByteArray) {
        checkAvailable()
        val data = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(marker)
            .setShouldBackupToCloud(false)
            .build()
        client.storeBytes(data).await()
    }

    // Without a Play services version that has Block Store, a call waits for an update (and notifies
    // the user) instead of failing; this check fails fast instead.
    private suspend fun checkAvailable() {
        GoogleApiAvailability.getInstance().checkApiAvailability(client).await()
    }

    // A failure must not look like a missing marker, and a cancelled task must not cancel the caller.
    // Results are delivered on the binder thread, not queued behind the app's main-thread startup.
    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener(DIRECT) { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener(DIRECT) { if (continuation.isActive) continuation.resumeWithException(it) }
            addOnCanceledListener(DIRECT) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Block Store task cancelled"))
                }
            }
        }

    private companion object {
        const val KEY = "apphud_install_marker"
        val DIRECT = Executor { it.run() }
    }
}
