package com.apphud.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.apphud.sdk.internal.ServiceLocator


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

    fun sdkVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    fun enableAllLogs() {
        logging = true
        httpLogging = true
    }

    /**
     * Override base URL for API requests.
     * Must be called after Apphud.start().
     *
     * @param baseUrl The base URL to use (e.g., "https://custom.gateway.com")
     * @param onError Callback invoked if ServiceLocator is not initialized
     */
    fun overrideBaseUrl(baseUrl: String, onError: ((Exception) -> Unit)? = null) {
        try {
            ServiceLocator.instance.urlProvider.updateBaseUrl(baseUrl)
        } catch (e: IllegalStateException) {
            onError?.invoke(e)
        }
    }

    internal fun setPackageName(packageName: String) {
        this.packageName = packageName
    }

    /**
     * Override base URL for API requests.
     * Must be called after Apphud.start().
     *
     * @param baseUrl The base URL to use (e.g., "https://custom.gateway.com")
     * @param onError Callback invoked if ServiceLocator is not initialized
     */
    fun overrideBaseUrl(baseUrl: String, onError: ((Exception) -> Unit)? = null) {
        try {
            ServiceLocator.instance.urlProvider.updateBaseUrl(baseUrl)
        } catch (e: IllegalStateException) {
            onError?.invoke(e)
        }
    }

    fun hasInternetConnection(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return if (connectivityManager != null) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager
                .getNetworkCapabilities(network)
            capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            false
        }
    }

    fun isEmulator(): Boolean = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
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
}
