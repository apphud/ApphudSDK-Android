package com.apphud.sdk

import java.text.SimpleDateFormat
import java.util.*

object DateTimeFormatter {
    private const val pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    val formatter =
        SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
}
