package com.apphud.sdk.internal.data.network

import com.apphud.sdk.APPHUD_NO_TIME_TO_RETRY
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
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
                response = if (tryCount == 0) {
                    // First try: use timeouts already set by TimeoutInterceptor
                    chain.proceed(request)
                } else {
                    // Retry attempts: override connect timeout for this retry
                    chain.withConnectTimeout(TimeoutInterceptor.TRY_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                        .proceed(request)
                }

                if (response.code in NO_RETRY_RANGE || response.code == TOO_MANY_REQUESTS) {
                    return response
                } else {
                    response.close()
                    tryCount++
                    if (tryCount == MAX_COUNT) error(APPHUD_NO_TIME_TO_RETRY)
                    Thread.sleep(RETRY_DELAY)
                }
            } catch (e: Exception) {
                // Don't retry on connection/timeout exceptions - let HostSwitcherInterceptor handle them
                if (e is java.net.SocketTimeoutException || e is java.net.UnknownHostException) {
                    throw e
                }
                
                tryCount++
                if (tryCount == MAX_COUNT) throw e
                Thread.sleep(RETRY_DELAY)
            }
        }
    }

    private companion object {
        const val RETRY_DELAY = 2_000L
        const val MAX_COUNT = 3
        const val TOO_MANY_REQUESTS = 429
        // Success, redirects, and client errors are final; only 5xx responses are retried.
        val NO_RETRY_RANGE = HttpURLConnection.HTTP_OK..499
    }
}