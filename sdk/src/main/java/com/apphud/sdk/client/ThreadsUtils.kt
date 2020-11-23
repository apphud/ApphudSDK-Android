package com.apphud.sdk.client

import com.apphud.sdk.tasks.PriorityComparator
import java.util.concurrent.*

internal class ThreadsUtils {

    companion object {
        private const val capacity = 10
    }

    private var pool = buildExecutor()
    private val service: ExecutorService = Executors.newSingleThreadExecutor()
    private val unlimited: ExecutorService = Executors.newSingleThreadExecutor()

    fun <T> registration(callable: Callable<T>, block: (T) -> Unit) =
        service.execute {
            try {
                block.invoke(callable.call())
            } catch (e: Exception) {
                pool.shutdown()
                pool = buildExecutor()
            }
        }

    fun allProducts(runnable: Runnable) = unlimited.execute(runnable)
    fun execute(runnable: Runnable) = pool.execute(runnable)

    private fun buildExecutor() = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(capacity, PriorityComparator()),
        Executors.defaultThreadFactory(),
        ThreadPoolExecutor.CallerRunsPolicy()
    )
}