package ru.rosbank.mbdg.myapplication.tasks

import java.util.concurrent.Callable

interface PriorityCallable<T> : Callable<T> {
    val priority: Int
}