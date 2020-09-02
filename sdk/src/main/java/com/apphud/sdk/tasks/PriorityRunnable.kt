package com.apphud.sdk.tasks

interface PriorityRunnable : Runnable {
    val priority: Int
}