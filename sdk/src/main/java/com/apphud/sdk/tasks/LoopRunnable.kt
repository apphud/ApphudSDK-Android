package com.apphud.sdk.tasks

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.client.NetworkException
import com.apphud.sdk.tasks.interrupted.LinearInterrupted
import com.apphud.sdk.tasks.interrupted.TimeInterruptedInteractor
import java.net.SocketTimeoutException
import java.net.UnknownHostException

//TODO количество логики в этом классе начинает увеличивать. Возможно скоро нужно будет разносить по разным файлам
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
            if(callable !is ErrorLogsCallable && callable.counter == 0){
                when (e) {
                    is UnknownHostException -> {
                        ApphudLog.log( "request failed with exception ${e.message}")
                    }
                    is SocketTimeoutException -> {
                        e.message?.let {
                            ApphudLog.log(
                                message = "request failed with exception ${e.message}",
                                sendLogToServer = true
                            )
                        }
                    }
                    else -> {
                        e.message?.let {
                            ApphudLog.log(
                                message = "request failed with exception ${e.message}",
                                sendLogToServer = true
                            )
                        }
                    }
                }
            }
            val exception = e as? NetworkException
            when (exception?.code) {
                401, 403 -> {
                    return ApphudLog.logI(message = "Response code: ${exception.code} signal for cancel request")
                }
                else -> {
                    try {
                        val timeout = interrupted.calculate(callable.counter)
                        ApphudLog.logI("sleep for $timeout milliseconds")
                        Thread.sleep(timeout)
                    } catch (e: InterruptedException) {
                        ApphudLog.log("InterruptedException: $e")
                    } finally {
                        ApphudLog.logI("finally restart task $callable with counter: ${callable.counter}")
                        callable.counter += 1
                        when {
                            callable.counter > COUNT -> {
                                ApphudLog.logI(message = "Stop retry $callable after $COUNT steps")
                            }
                            else -> run()
                        }
                    }
                }
            }
        }
    }
}
