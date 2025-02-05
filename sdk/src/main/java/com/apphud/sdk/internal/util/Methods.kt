package com.apphud.sdk.internal.util

import kotlin.coroutines.cancellation.CancellationException

@Suppress("TooGenericExceptionCaught")
internal inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }