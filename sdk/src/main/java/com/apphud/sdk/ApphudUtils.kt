package com.apphud.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * This class will contain some utils, more will be added in the future.
 */
object ApphudUtils {
    var packageName: String = ""
        private set

    var logging: Boolean = false
        private set

    var httpLogging: Boolean = false
        private set

    var optOutOfTracking: Boolean = false

    /**
     * Enable console logging.
     */
    fun enableDebugLogs() {
        logging = true
    }

    fun enableAllLogs() {
        logging = true
        httpLogging = true
    }

    internal fun setPackageName(packageName: String) {
        this.packageName = packageName
    }

    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                }
            }
        }
        return false
    }

    fun isEmulator() : Boolean
         = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")

    fun getInstallerPackageName(context: Context): String? {
        kotlin.runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                return context.packageManager.getInstallSourceInfo(packageName).installingPackageName
            @Suppress("DEPRECATION")
            return context.packageManager.getInstallerPackageName(packageName)
        }
        return null
    }

    fun isFromPlayMarket() :Boolean{
        val installer = getInstallerPackageName(ApphudInternal.context)
        ApphudLog.log("Installer: ${installer}")
        return installer == "com.android.vending"
    }
}
