package com.apphud.sdk.tasks

class PriorityComparator<T : Runnable> : Comparator<T> {
    override fun compare(left: T, right: T): Int =
        (left as PriorityRunnable).priority.compareTo((right as PriorityRunnable).priority)
}