package com.apphud.sdk.tasks.interrupted

import com.apphud.sdk.Milliseconds

class LinearInterrupted : TimeInterruptedInteractor {

    companion object {
        private const val STEP = 5_000L
    }

    override fun calculate(count: Int): Milliseconds = (count + 1) * STEP
}