package ru.rosbank.mbdg.myapplication.tasks

interface PriorityRunnable : Runnable {
    val priority: Int
}