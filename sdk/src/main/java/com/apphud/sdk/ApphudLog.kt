package com.apphud.sdk

import android.util.Log
import com.apphud.sdk.body.BenchmarkBody
import com.apphud.sdk.managers.RequestManager
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.pow
import kotlin.math.roundToInt

internal object ApphudLog {
    private const val TAG = "Apphud"
    val data = mutableListOf<Map<String, Any?>>()

    /**
     * This is a fun for log messages.
     * */
    fun log(
        message: String,
        sendLogToServer: Boolean = false,
    ) {
        if (ApphudUtils.logging) {
            Log.d(TAG, message)
        }
        if (sendLogToServer) {
            sendErrorLogs(message)
        }
    }

    /**
     * This is a fun for log info messages.
     * */
    fun logI(
        message: String,
        sendLogToServer: Boolean = false,
    ) {
        if (ApphudUtils.logging) {
            Log.i(TAG, message)
        }
        if (sendLogToServer) {
            sendErrorLogs(message)
        }
    }

    /**
     * This is a fun to force error message logging.
     * */
    fun logE(
        message: String,
        sendLogToServer: Boolean = false,
    ) {
        Log.e(TAG, message)

        if (sendLogToServer) {
            sendErrorLogs(message)
        }
    }

    /**
     * Send Error Logs to Apphud Server
     * */
    private fun sendErrorLogs(message: String) {
        // ApphudInternal.sendErrorLogs(message)
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

    fun Double.roundTo(numFractionDigits: Int): Double {
        val factor = 10.0.pow(numFractionDigits.toDouble())
        return (this * factor).roundToInt() / factor
    }

    var timer: Timer? = null

    fun startTimer() {
        if (timer == null) {
            timer =
                fixedRateTimer(name = "benchmark_timer", initialDelay = 5000, period = 5000) {
                    if (data.isNotEmpty()) {
                        var body: BenchmarkBody?
                        synchronized(data) {
                            val listToSend = mutableListOf<Map<String, Any?>>()
                            listToSend.addAll(data)
                            body =
                                BenchmarkBody(
                                    device_id = ApphudInternal.deviceId,
                                    user_id = ApphudInternal.userId,
                                    bundle_id = ApphudInternal.getPackageName(),
                                    data = listToSend,
                                )
                            data.clear()
                        }
                        body?.let {
                            RequestManager.sendBenchmarkLogs(it)
                        }
                    } else {
                        timer?.cancel()
                        timer = null
                    }
                }
        }
    }
}
