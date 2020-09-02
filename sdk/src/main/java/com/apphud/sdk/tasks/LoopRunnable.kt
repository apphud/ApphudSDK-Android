package com.apphud.sdk.tasks

import com.apphud.sdk.ApphudLog

class LoopRunnable<T>(
    private val callable: PriorityCallable<T>,
    private val callback: (T) -> Unit
) : PriorityRunnable {

    override val priority: Int = callable.priority
    override fun run() {
        try {
            callback.invoke(callable.call())
        } catch (e: Exception) {
            ApphudLog.log("CallbackCallable with $e")
            try {
                ApphudLog.log("CallbackCallable BEFORE sleep")
                Thread.sleep(10_000)
                ApphudLog.log("CallbackCallable AFTER sleep")
            } catch (e: InterruptedException) {
                ApphudLog.log("CallbackCallable InterruptedException: $e")
            } finally {
                ApphudLog.log("CallbackCallable FINALLY")
                run()
            }
        }
    }
}