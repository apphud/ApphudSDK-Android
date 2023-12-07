package com.apphud.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import java.util.concurrent.atomic.AtomicInteger

internal fun Context.isDebuggable(): Boolean {
    return 0 != this.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
}

internal fun AtomicInteger.isBothLoaded(): Boolean {
    return this.get() == 2
}

enum class ApphudBillingResponseCodes(val code: Int) {
    SERVICE_TIMEOUT(-3),
    FEATURE_NOT_SUPPORTED(-2),
    SERVICE_DISCONNECTED(-1),
    OK(0),
    USER_CANCELED(1),
    SERVICE_UNAVAILABLE(2),
    BILLING_UNAVAILABLE(3),
    ITEM_UNAVAILABLE(4),
    DEVELOPER_ERROR(5),
    ERROR(6),
    ITEM_ALREADY_OWNED(7),
    ITEM_NOT_OWNED(8),
    NETWORK_ERROR(12),
    ;

    companion object {
        fun getName(code: Int): String {
            return values().firstOrNull { it.code == code }?.name ?: "UNKNOWN"
        }
    }
}
