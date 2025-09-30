package com.apphud.sdk.internal.data.network

import com.apphud.sdk.APPHUD_ERROR_TIMEOUT
import com.apphud.sdk.APPHUD_NO_TIME_TO_RETRY
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_BAD_GATEWAY
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.util.concurrent.TimeUnit

internal class HttpRetryInterceptor : Interceptor {

    @Suppress("TooGenericExceptionCaught")
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response: Response?
        var tryCount = 0

        while (true) {
            try {
                val connectTimeout = if (tryCount == 0) {
                    FIRST_TRY_CONNECT_TIMEOUT
                } else {
                    TRY_CONNECT_TIMEOUT
                }

                response = chain
                    .withConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                    .proceed(request)

                if (response.code in NO_RETRY_RANGE || response.code == TOO_MANY_REQUESTS) {
                    return response
                } else {
                    tryCount++
                    if (tryCount == MAX_COUNT) error(APPHUD_NO_TIME_TO_RETRY)
                    Thread.sleep(RETRY_DELAY)
                }
            } catch (e: Exception) {
                tryCount++
                if (tryCount == MAX_COUNT) throw e
                Thread.sleep(RETRY_DELAY)
            }
        }
    }

    private companion object {
        const val RETRY_DELAY = 2_000L
        const val MAX_COUNT = 3
        const val FIRST_TRY_CONNECT_TIMEOUT = 2
        const val TRY_CONNECT_TIMEOUT = 5
        val FALLBACK_ERRORS = setOf(
            APPHUD_ERROR_TIMEOUT,
            HTTP_NOT_FOUND,
            HTTP_INTERNAL_ERROR,
            HTTP_BAD_GATEWAY,
            HTTP_UNAVAILABLE
        )
        const val TOO_MANY_REQUESTS = 429
        val NO_RETRY_RANGE = HttpURLConnection.HTTP_OK..HTTP_FORBIDDEN
    }
}