package com.apphud.sdk.internal.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build
import android.provider.Settings
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.internal.domain.model.DeviceIdentifiers
import com.apphud.sdk.managers.AdvertisingIdManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class DeviceIdentifiersDataSource(
    private val applicationContext: Context,
    private val storage: SharedPreferencesStorage,
) {

    fun loadCached(): DeviceIdentifiers {
        val ids = storage.deviceIdentifiers
        return DeviceIdentifiers(
            advertisingId = ids[0].ifEmpty { null },
            appSetId = ids[1].ifEmpty { null },
            androidId = ids[2].ifEmpty { null },
        )
    }

    fun save(identifiers: DeviceIdentifiers) {
        storage.deviceIdentifiers = arrayOf(
            identifiers.advertisingId.orEmpty(),
            identifiers.appSetId.orEmpty(),
            identifiers.androidId.orEmpty(),
        )
    }

    suspend fun fetchIdentifiers(): DeviceIdentifiers = coroutineScope {
        val adIdDeferred = async { fetchAdvertisingId() }
        val appSetIdDeferred = async { fetchAppSetId() }
        val androidIdDeferred = async { fetchAndroidId() }

        DeviceIdentifiers(
            advertisingId = adIdDeferred.await(),
            appSetId = appSetIdDeferred.await(),
            androidId = androidIdDeferred.await(),
        )
    }

    fun fetchAndroidIdSync(): String? =
        Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)

    private suspend fun fetchAdvertisingId(): String? =
        suspendCancellableCoroutine { continuation ->
            if (hasAdIdPermission()) {
                ApphudLog.logI("Started fetching AD ID")
                var advId: String? = null
                try {
                    val adInfo: AdvertisingIdManager.AdInfo =
                        AdvertisingIdManager.getAdvertisingIdInfo(applicationContext)
                    advId = adInfo.id
                } catch (e: Exception) {
                    ApphudLog.logE("Failed to fetch AD ID: $e")
                }

                ApphudLog.logI("Finished fetching AD ID $advId")

                if (continuation.isActive) {
                    if (advId == null || advId == "00000000-0000-0000-0000-000000000000") {
                        ApphudLog.log("Unable to fetch Advertising ID, please check AD_ID permission in the manifest file.")
                        continuation.resume(null)
                    } else {
                        continuation.resume(advId)
                    }
                }
            } else {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private suspend fun fetchAppSetId(): String? =
        suspendCancellableCoroutine { continuation ->
            ApphudLog.logI("Started fetching App Set ID")
            val client = AppSet.getClient(applicationContext)
            val task: Task<AppSetIdInfo> = client.appSetIdInfo
            task.addOnSuccessListener {
                val id: String = it.id
                ApphudLog.logI("Finished fetching App Set ID $id")
                if (continuation.isActive) {
                    continuation.resume(id)
                }
            }
            task.addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
            task.addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private suspend fun fetchAndroidId(): String? =
        suspendCancellableCoroutine { continuation ->
            ApphudLog.logI("Started fetching Android ID")
            val androidId: String? = fetchAndroidIdSync()
            ApphudLog.logI("Finished fetching Android ID")
            if (continuation.isActive) {
                continuation.resume(androidId)
            }
        }

    private fun hasAdIdPermission(): Boolean {
        try {
            val pInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applicationContext.packageManager.getPackageInfo(
                        ApphudUtils.packageName,
                        PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                    )
                } else {
                    applicationContext.packageManager.getPackageInfo(
                        ApphudUtils.packageName,
                        PackageManager.GET_PERMISSIONS,
                    )
                }

            if (pInfo.requestedPermissions != null) {
                for (p in pInfo.requestedPermissions) {
                    if (p == AD_ID_PERMISSION) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private companion object {
        const val AD_ID_PERMISSION = "com.google.android.gms.permission.AD_ID"
    }
}
