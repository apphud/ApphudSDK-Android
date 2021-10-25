package com.apphud.sdk

import android.util.Log
import com.apphud.sdk.client.ApphudClient
import com.apphud.sdk.managers.RequestManager

internal object ApphudLog {

    private const val TAG = "Apphud"

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
    fun logE(message: String, sendLogToServer:Boolean = false) {
        Log.e(TAG, message)

        if(sendLogToServer){
            sendErrorLogs(message)
        }
    }

    /**
     * Send Error Logs to Apphud Server
     * */
    private fun sendErrorLogs(message: String) {
        RequestManager.sendErrorLogs(message)
    }

}