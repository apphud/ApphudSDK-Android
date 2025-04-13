package com.apphud.sdk.internal.util

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@Suppress("TooGenericExceptionCaught")
internal inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

fun <T> CancellableContinuation<T>.resumeIfActive(value: T): Boolean {
    return if (isActive) {
        resume(value)
        true
    } else {
        false
    }
}