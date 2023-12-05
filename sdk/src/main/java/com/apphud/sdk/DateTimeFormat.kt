package com.apphud.sdk

import java.text.SimpleDateFormat
import java.util.*

fun buildTime(source: Long?): String? =
    try {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        source?.let { formatter.format(Date(source)) }
    } catch (e: Exception) {
        ApphudLog.log("Wrong parse time: $source")
        null
    }
