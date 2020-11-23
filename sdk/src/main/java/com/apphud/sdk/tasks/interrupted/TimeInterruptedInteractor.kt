package com.apphud.sdk.tasks.interrupted

import com.apphud.sdk.Milliseconds

/**
 * Интерфейс определяющий сколько времени нужно ждать перед следующей попыткой
 * @param count - Номер попытки
 * return value in milliseconds
 */
interface TimeInterruptedInteractor {
    fun calculate(count: Int): Milliseconds
}