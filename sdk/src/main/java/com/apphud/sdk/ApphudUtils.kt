package com.apphud.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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

    fun getSdkVersion() : String{
       return BuildConfig.VERSION_NAME
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
}
