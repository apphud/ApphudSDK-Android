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

    /**
     * Send Benchmark Logs to Apphud Server
     * */
    fun logBenchmark(
        path: String,
        time: Long,
    ) {
        if (path == "/v1/customers" ||
            path == "/v2/products" ||
            path == "/v2/paywall_configs" ||
            path == "/v1/subscriptions"
        ) {
            logI("Benchmark: " + path + ": " + time + "ms")
            /*val seconds: Double = time / 1000.0
            synchronized(data){
                val logItem: MutableMap<String, Any?> = mutableMapOf(
                    "path" to path,
                    "duration" to seconds   //.roundTo(2)
                )
                data.add(logItem)
            }
            startTimer()*/
        }
    }
}
