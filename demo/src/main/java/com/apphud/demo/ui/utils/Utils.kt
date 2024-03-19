package com.apphud.demo.ui.utils

import java.text.SimpleDateFormat
import java.util.*

fun convertLongToTime(time: Long): String {
    val date = Date(time)
    val format = SimpleDateFormat("d MMM yyyy HH:mm:ss 'GMT'Z")
    return format.format(date)
}

fun getRecurrenceModeStr(mode: Int): String {
    return when (mode) {
        1 -> " {INFINITE}"
        2 -> " {FINITE}"
        3 -> " {NON_RECURRING}"
        else -> {
            ""
        }
    }
}
