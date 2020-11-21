package com.apphud.sdk.tasks

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.tasks.interrupted.LinearInterrupted
import com.apphud.sdk.tasks.interrupted.TimeInterruptedInteractor

class LoopRunnable<T>(
    private val callable: PriorityCallable<T>,
    private val interrupted: TimeInterruptedInteractor = LinearInterrupted(),
    private val callback: (T) -> Unit
) : PriorityRunnable {

    companion object {
        private const val COUNT = 10
    }

    override val priority: Int = callable.priority
    override fun run() {
        try {
            callback.invoke(callable.call())
        } catch (e: Exception) {
            ApphudLog.log("Throw with exception: $e")
            try {
                val timeout = interrupted.calculate(callable.counter)
                ApphudLog.log("sleep for $timeout milliseconds")
                Thread.sleep(timeout)
            } catch (e: InterruptedException) {
                ApphudLog.log("InterruptedException: $e")
            } finally {
                ApphudLog.log("finally restart task $callable with counter: ${callable.counter}")
                callable.counter += 1
                when {
                    callable.counter > COUNT -> ApphudLog.log("Stop retry $callable after $COUNT steps")
                    else                     -> run()
                }
            }
        }
    }
}