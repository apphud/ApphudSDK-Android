package com.apphud.sdk.internal.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

internal inline fun <R, T> Result<T>.mapCatchingCancellable(transform: (value: T) -> R): Result<R> =
    runCatchingCancellable {
        transform(getOrThrow())
    }

internal inline fun <R, T : R> Result<T>.recoverCatchingCancellable(
    crossinline transform:  (exception: Throwable) -> R
): Result<R> {
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> runCatchingCancellable { transform(exception) }
    }
}

fun Job?.isActive(): Boolean =
    this?.isActive == true

fun <T> CancellableContinuation<T>.resumeIfActive(value: T): Boolean {
    return if (isActive) {
        resume(value)
        true
    } else {
        false
    }
}

/**
 * Wraps a nullable callback with a single parameter to execute on the main thread.
 *
 * Example usage:
 * ```
 * val wrappedCallback = callback.wrapToMainThread(coroutineScope)
 * someAsyncOperation(wrappedCallback)
 * ```
 *
 * @param scope The CoroutineScope to launch the main thread execution
 * @return A wrapped callback that executes on the main thread, or null if the original callback is null
 */
internal inline fun <T> ((T) -> Unit)?.wrapToMainThread(
    scope: CoroutineScope
): ((T) -> Unit)? = this?.let { callback ->
    { result ->
        scope.launch(Dispatchers.Main) {
            callback(result)
        }
    }
}

/**
 * Wraps a nullable callback with two parameters to execute on the main thread.
 *
 * Example usage:
 * ```
 * val wrappedCallback = callback.wrapToMainThread(coroutineScope)
 * someAsyncOperation(wrappedCallback)
 * ```
 *
 * @param scope The CoroutineScope to launch the main thread execution
 * @return A wrapped callback that executes on the main thread, or null if the original callback is null
 */
internal inline fun <T1, T2> ((T1, T2) -> Unit)?.wrapToMainThread(
    scope: CoroutineScope
): ((T1, T2) -> Unit)? = this?.let { callback ->
    { result1, result2 ->
        scope.launch(Dispatchers.Main) {
            callback(result1, result2)
        }
    }
}