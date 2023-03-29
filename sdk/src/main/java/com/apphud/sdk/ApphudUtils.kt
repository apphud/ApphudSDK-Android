package com.apphud.sdk

/**
 * This class will contain some utils, more will be added in the future.
 */
internal object ApphudUtils {

    var packageName: String = ""
        private set

    var logging: Boolean = false
        private set

    var optOutOfTracking: Boolean = false

    /**
     * Enable console logging.
     */
    fun enableDebugLogs() {
        logging = true
    }

    fun setPackageName(packageName: String) {
        this.packageName = packageName
    }
}