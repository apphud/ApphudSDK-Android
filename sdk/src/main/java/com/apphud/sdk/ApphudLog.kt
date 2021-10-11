package com.apphud.sdk

import android.util.Log
import com.apphud.sdk.client.ApphudClient

internal object ApphudLog {

    private const val TAG = "Apphud"

    private var client: ApphudClient? = null

    /**
     * This is a fun for log messages.
     * */
    fun log(message: String, sendLogToServer:Boolean = false) {
        if (ApphudUtils.logging) {
            Log.d(TAG, message)
        }
        if(sendLogToServer){
            sendErrorLogs(message)
        }
    }

    /**
     * This is a fun for log info messages.
     * */
    fun logI(message: String, sendLogToServer:Boolean = false) {
        if (ApphudUtils.logging) {
            Log.i(TAG, message)
        }
        if(sendLogToServer){
            sendErrorLogs(message)
        }
    }

    /**
     * This is a fun to force error message logging.
     * */
    fun logE(message: String) {
        Log.e(TAG, message)
        sendErrorLogs(message)
    }

    fun setClient(client: ApphudClient) {
        this.client = client
    }

    /**
     * Send Error Logs to Apphud Server
     * */
    private fun sendErrorLogs(message: String) {
        client?.sendErrorLogs(
            ApphudInternal.makeErrorLogsBody(message, ApphudUtils.packageName)
        )
    }

}