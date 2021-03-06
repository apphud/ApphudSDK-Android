package com.apphud.sdk.client

interface NetworkExecutor {
    fun <O> call(config: RequestConfig): O
    fun <I, O> call(config: RequestConfig, input: I?): O
}