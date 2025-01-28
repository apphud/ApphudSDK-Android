package com.apphud.sdk

import android.content.Context
import android.content.pm.PackageManager

internal fun Context.buildAppVersion(): String =
    try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "not found application version"
    }
