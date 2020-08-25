package ru.rosbank.mbdg.myapplication

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

inline fun <V> ExecutorService.submitConnect(
    crossinline connection: () -> Boolean,
    crossinline submitter: () -> V
): Future<V> = submit<V> {
    while (!connection()) {
    }
    submitter()
}