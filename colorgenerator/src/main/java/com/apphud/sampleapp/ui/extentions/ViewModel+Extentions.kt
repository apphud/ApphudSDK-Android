package com.apphud.sampleapp.ui.extentions

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import java.io.IOException

suspend fun <T> ViewModel.retry(
    times: Int = Int.MAX_VALUE,
    initialDelay: Long = 500, // 0.5 second
    maxDelay: Long = 2000,    // 1 second
    factor: Double = 2.0,
    block: suspend () -> T): T {

    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: IOException) {
            // you can log an error here and/or make a more finer-grained
            // analysis of the cause to see if retry is needed
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // last attempt
}