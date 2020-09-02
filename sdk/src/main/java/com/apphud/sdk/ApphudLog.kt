package com.apphud.sdk

import android.util.Log

internal object ApphudLog {

    private const val TAG = "Apphud"

    fun log(message: String) {
        if (ApphudUtils.logging) {
            Log.e(TAG, message)
        }
    }
}