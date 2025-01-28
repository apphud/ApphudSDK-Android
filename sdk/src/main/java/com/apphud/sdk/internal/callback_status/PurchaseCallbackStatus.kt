package com.apphud.sdk.internal.callback_status

internal sealed class PurchaseCallbackStatus {
    class Success(val message: String? = null) : PurchaseCallbackStatus()

    class Error(val error: String) : PurchaseCallbackStatus()
}
