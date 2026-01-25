package com.apphud.sdk.internal.provider;

import android.content.Context
import android.os.Build
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudInternal.fetchAndroidIdSync
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.BuildConfig
import com.apphud.sdk.buildAppVersion
import com.apphud.sdk.isDebuggable
import com.apphud.sdk.storage.Storage
import java.util.Locale
import java.util.TimeZone

@Suppress("TooManyFunctions")
internal class RegistrationProvider(
    private val applicationContext: Context,
    private val storage: Storage,
) {

    fun getLocale(): String = Locale.getDefault().toString()

    fun getSdkVersion(): String = BuildConfig.VERSION_NAME

    fun getAppVersion(): String = applicationContext.buildAppVersion()

    fun getDeviceFamily(): String = Build.MANUFACTURER

    fun getPlatform(): String = PLATFORM

    fun getDeviceType(): String =
        if (ApphudUtils.optOutOfTracking) "Restricted" else Build.MODEL

    fun getOsVersion(): String = Build.VERSION.RELEASE

    fun getStartAppVersion(): String = applicationContext.buildAppVersion()

    fun getIdfa(): String? {
        val idfa = storage.deviceIdentifiers[0]
        return if (ApphudUtils.optOutOfTracking || idfa.isEmpty()) null
        else idfa
    }

    fun getIdfv(): String? {
        val appSetId = storage.deviceIdentifiers[1]
        return if (ApphudUtils.optOutOfTracking || appSetId.isEmpty()) null
        else appSetId
    }

    fun getAndroidId(): String? {
        var androidId = storage.deviceIdentifiers[2]
        if (androidId.isEmpty()) {
            fetchAndroidIdSync()?.let {
                androidId = it
            }
        }
        return if (ApphudUtils.optOutOfTracking || androidId.isEmpty()) null
        else androidId
    }

    fun getDeviceId(): String? = ApphudInternal.deviceId

    fun getTimeZone(): String = TimeZone.getDefault().id

    fun isSandbox(): Boolean = applicationContext.isDebuggable()

    fun getFirstSeen(): Long? =
        getInstallationDate()

    fun getSdkLaunchedAt(): Long = ApphudInternal.sdkLaunchedAt

    fun getRequestTime(): Long = System.currentTimeMillis()

    fun getInstallSource(): String =
        ApphudUtils.getInstallerPackageName(applicationContext) ?: "unknown"

    fun getObserverMode(): Boolean = ApphudInternal.observerMode

    fun getFromWeb2Web(): Boolean = ApphudInternal.fromWeb2Web

    private fun getInstallationDate(): Long? {
        var dateInSecond: Long? = null
        try {
            this.applicationContext.packageManager?.let { manager ->
                dateInSecond = manager.getPackageInfo(this.applicationContext.packageName, 0).firstInstallTime / 1000L
            }
        } catch (ex: Exception) {
            ex.message?.let {
                ApphudLog.logE(it)
            }
        }
        return dateInSecond
    }

    private companion object {
        const val PLATFORM = "Android"
    }
}