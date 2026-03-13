package com.apphud.sdk.internal.provider

import android.content.Context
import android.os.Build
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.BuildConfig
import com.apphud.sdk.buildAppVersion
import com.apphud.sdk.internal.data.AnalyticsTracker
import com.apphud.sdk.internal.data.DeviceIdentifiersRepository
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.isDebuggable
import java.util.Locale
import java.util.TimeZone

@Suppress("TooManyFunctions")
internal class RegistrationProvider(
    private val applicationContext: Context,
    private val deviceIdentifiersRepository: DeviceIdentifiersRepository,
    private val userRepository: UserRepository,
    private val analyticsTracker: AnalyticsTracker,
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
        if (ApphudUtils.optOutOfTracking) return null
        val idfa = deviceIdentifiersRepository.getIdentifiers().advertisingId
        return if (idfa.isNullOrEmpty()) null else idfa
    }

    fun getIdfv(): String? {
        if (ApphudUtils.optOutOfTracking) return null
        val appSetId = deviceIdentifiersRepository.getIdentifiers().appSetId
        return if (appSetId.isNullOrEmpty()) null else appSetId
    }

    fun getAndroidId(): String? {
        if (ApphudUtils.optOutOfTracking) return null
        var androidId = deviceIdentifiersRepository.getIdentifiers().androidId
        if (androidId.isNullOrEmpty()) {
            androidId = deviceIdentifiersRepository.fetchAndroidIdSync()
        }
        return if (androidId.isNullOrEmpty()) null else androidId
    }

    fun getUserId(): String? = userRepository.getUserId()

    fun getDeviceId(): String? = userRepository.getDeviceId()

    fun getTimeZone(): String = TimeZone.getDefault().id

    fun isSandbox(): Boolean = applicationContext.isDebuggable()

    fun getFirstSeen(): Long? =
        getInstallationDate()

    fun getSdkLaunchedAt(): Long = analyticsTracker.sdkLaunchTimeMs()

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