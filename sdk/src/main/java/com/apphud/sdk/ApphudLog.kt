package com.apphud.sdk

import android.util.Log

internal object ApphudLog {
    private const val TAG = "ApphudLogs"
    val data = mutableListOf<Map<String, Any?>>()

    /**
     * This is a fun for log messages.
     * */
    fun log(
        message: String,
    ) {
        if (ApphudUtils.logging) {
            Log.d(TAG, message)
        }
    }

    /**
     * This is a fun for log info messages.
     * */
    fun logI(
        message: String,
    ) {
        if (ApphudUtils.logging) {
            Log.i(TAG, message)
        }
    }

    /**
     * This is a fun to force error message logging.
     * */
    fun logE(
        message: String,
    ) {
        Log.e(TAG, message)
    }
}
