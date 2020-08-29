package ru.rosbank.mbdg.myapplication

import android.util.Log

object ApphudLog {

    private const val TAG = "Apphud"

    fun log(message: String) {
        if (ApphudUtils.logging) {
            Log.e(TAG, message)
        }
    }
}