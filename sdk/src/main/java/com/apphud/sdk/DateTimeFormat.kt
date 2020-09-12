package com.apphud.sdk

import java.text.SimpleDateFormat

object DateTimeFormat {
    const val common = "yyyy-MM-dd'T'HH:mm:ss"
}

fun buildTime(source: String): String? = try {
    val parser = SimpleDateFormat(DateTimeFormat.common)
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm")
    formatter.format(parser.parse(source))
} catch (e: Exception) {
    ApphudLog.log("Wrong parse time: $source")
    null
}