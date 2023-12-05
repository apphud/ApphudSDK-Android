package com.apphud.sdk

import java.util.*

fun Locale.formatString() =
    buildString {
        append(language)
        append("_")
        append(country)
    }
