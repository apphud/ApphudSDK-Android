package com.apphud.sdk.tasks

class PriorityComparator<T : Runnable> : Comparator<T> {
    override fun compare(left: T, right: T): Int =
        (left as com.apphud.sdk.tasks.PriorityRunnable).priority.compareTo((right as com.apphud.sdk.tasks.PriorityRunnable).priority)
}