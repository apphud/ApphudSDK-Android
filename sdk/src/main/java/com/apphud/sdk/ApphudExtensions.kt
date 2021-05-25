package com.apphud.sdk

import android.content.Context
import android.content.pm.ApplicationInfo

internal fun Context.isDebuggable(): Boolean {
    return 0 != this.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
}

internal fun Int.isBoothSkuLoaded(): Boolean {
    return this == 2
}