package com.apphud.sdk

/**
 * This class will contain some utils, more will be added in the future.
 */
internal object ApphudUtils {

    var logging: Boolean = false
        private set

    var adTracking: Boolean = true
        private set

    /**
     * Enable console logging.
     */
    fun enableDebugLogs() {
        logging = true
    }

    fun disableAdTracking() {
        adTracking = true
    }
}