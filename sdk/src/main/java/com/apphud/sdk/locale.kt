package com.apphud.sdk

import java.util.*

internal fun Locale.formatString() =
    buildString {
        append(language)
        append("_")
        append(country)
    }
