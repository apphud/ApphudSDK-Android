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
