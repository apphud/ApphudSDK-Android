package com.apphud.sdk.internal

import java.io.Closeable

abstract class BaseAsyncWrapper : Closeable {
    val retryCapacity: Int = 10
    var retryCount: Int = 0
    var retryDelay: Long = 350
}
