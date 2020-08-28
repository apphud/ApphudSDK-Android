package ru.rosbank.mbdg.myapplication.tasks

interface Callback<T> {
    fun finish(result: T)
}