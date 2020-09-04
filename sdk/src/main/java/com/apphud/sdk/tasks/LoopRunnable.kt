package com.apphud.sdk.tasks

import com.apphud.sdk.ApphudLog

class LoopRunnable<T>(
    private val callable: PriorityCallable<T>,
    private val callback: (T) -> Unit
) : PriorityRunnable {

    private var timeout: Long = 10_000

    override val priority: Int = callable.priority
    override fun run() {
        try {
            callback.invoke(callable.call())
        } catch (e: Exception) {
            ApphudLog.log("Throw with exception: $e")
            try {
                timeout += callable.incrementMilliseconds
                ApphudLog.log("sleep for $timeout milliseconds")
                Thread.sleep(timeout)
            } catch (e: InterruptedException) {
                ApphudLog.log("InterruptedException: $e")
            } finally {
                ApphudLog.log("finally restart task $callable")
                run()
            }
        }
    }
}