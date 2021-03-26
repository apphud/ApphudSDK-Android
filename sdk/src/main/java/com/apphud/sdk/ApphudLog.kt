package com.apphud.sdk

import android.util.Log

internal object ApphudLog {

    private const val TAG = "Apphud"

    /**
     * This is a fun for log messages.
     * */
    fun log(message: String) {
        if (ApphudUtils.logging) {
            Log.e(TAG, message)
        }
    }

    /**
     * This is a fun to force error message logging.
    * */
    fun logE(message: String) {
        Log.e(TAG, message)
    }
}