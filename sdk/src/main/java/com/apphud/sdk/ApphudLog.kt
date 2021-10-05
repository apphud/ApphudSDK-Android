package com.apphud.sdk

import android.util.Log
import com.apphud.sdk.client.ApphudClient

internal object ApphudLog {

    private const val TAG = "Apphud"

    private var client: ApphudClient? = null

    /**
     * This is a fun for log messages.
     * */
    fun log(message: String, apphud_product_id: String? = null, sendLogToServer:Boolean = false) {
        if (ApphudUtils.logging) {
            Log.e(TAG, message)
        }
        if(sendLogToServer){
            sendErrorLogs(message, apphud_product_id)
        }
    }

    /**
     * This is a fun for log info messages.
     * */
    fun logI(message: String, apphud_product_id: String? = null, sendLogToServer:Boolean = false) {
        if (ApphudUtils.logging) {
            Log.i(TAG, message)
        }
        if(sendLogToServer){
            sendErrorLogs(message, apphud_product_id)
        }
    }

    /**
     * This is a fun to force error message logging.
     * */
    fun logE(message: String, apphud_product_id: String? = null) {
        Log.e(TAG, message)
        sendErrorLogs(message, apphud_product_id)
    }

    fun setClient(client: ApphudClient) {
        this.client = client
    }

    /**
     * Send Error Logs to Apphud Server
     * */
    private fun sendErrorLogs(message: String, apphud_product_id: String? = null) {
        client?.sendErrorLogs(
            ApphudInternal.makeErrorLogsBody(message, apphud_product_id)
        )
    }

}