package ru.rosbank.mbdg.myapplication.tasks

import android.util.Log

class LoopRunnable<T>(
    private val callable: PriorityCallable<T>,
    private val callback: (T) -> Unit
) : PriorityRunnable {

    override val priority: Int = callable.priority
    override fun run() {
        try {
            callback.invoke(callable.call())
        } catch (e: Exception) {
            Log.e("WOW", "CallbackCallable with $e")
            try {
                Log.e("WOW", "CallbackCallable BEFORE sleep")
                Thread.sleep(10_000)
                Log.e("WOW", "CallbackCallable AFTER sleep")
            } catch (e: InterruptedException) {
                Log.e("WOW", "CallbackCallable InterruptedException: $e")
            } finally {
                Log.e("WOW", "CallbackCallable FINALLY")
                run()
            }
        }
    }
}