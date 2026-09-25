package com.apphud.sdk.internal.data.network

import com.apphud.sdk.ApphudLog
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the client session id. Runs before the retry and host-switch interceptors, so every
 * attempt of one call carries the id the call started with.
 */
internal class SessionIdInterceptor(
    private val sessionIdProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sessionId = sessionIdProvider()
        // OkHttp rejects header values outside printable ASCII; a host-supplied id must not
        // fail the SDK's requests.
        val sessionRequest = runCatching {
            request.newBuilder().header(HEADER, sessionId).build()
        }.getOrElse {
            ApphudLog.logE("Session id is not a valid header value, request sent without it")
            request
        }
        return chain.proceed(sessionRequest)
    }

    companion object {
        const val HEADER = "X-Apphud-Session-Id"
    }
}
