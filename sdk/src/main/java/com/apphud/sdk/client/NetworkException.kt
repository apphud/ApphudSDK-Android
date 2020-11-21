package com.apphud.sdk.client

data class NetworkException(val code: Int) : RuntimeException()

internal fun exception(code: Int): Nothing = throw NetworkException(code)