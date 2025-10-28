package com.apphud.sdk.internal.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal class TimeoutInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val chainWithConnectTimeout = chain.withConnectTimeout(FIRST_TRY_CONNECT_TIMEOUT, TimeUnit.SECONDS)

        return if (request.url.toString().contains("/customers")) {
            chainWithConnectTimeout
                .withReadTimeout(CUSTOMERS_READ_TIMEOUT, TimeUnit.SECONDS)
                .proceed(request)
        } else {
            chainWithConnectTimeout.proceed(request)
        }
    }

    companion object {
        const val FIRST_TRY_CONNECT_TIMEOUT = 2
        const val TRY_CONNECT_TIMEOUT = 5
        const val CUSTOMERS_READ_TIMEOUT = 7
    }
}
