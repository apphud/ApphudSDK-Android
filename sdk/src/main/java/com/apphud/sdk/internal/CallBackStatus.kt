package com.apphud.sdk.internal

sealed class CallBackStatus {
    class Success(val message: String? = null) : CallBackStatus()
    class Error(val error: String) : CallBackStatus()
}
