package com.apphud.sdk.tasks

import java.util.concurrent.Callable

interface PriorityCallable<T> : Callable<T> {
    val priority: Int
    val incrementMilliseconds: Long
        get() = 0
    var counter: Int
}