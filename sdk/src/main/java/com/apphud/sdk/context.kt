package com.apphud.sdk

import android.content.Context
import android.content.pm.PackageManager

internal fun Context.buildAppVersion(): String =
    try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "not found application version"
    } catch (e: PackageManager.NameNotFoundException) {
        "not found application version"
    }

/**
 * Whether the app hasn't been updated since it was installed: true on a first install and on a
 * reinstall, false once an update has run. Null when the package info isn't available.
 */
internal fun Context.isFreshPackageInstall(): Boolean? =
    runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        // Greater for an install into another user or profile: its install time is per user.
        info.firstInstallTime >= info.lastUpdateTime
    }.getOrNull()
