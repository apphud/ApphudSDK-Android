package com.apphud.sdk.internal.data.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.UnknownHostException

internal class HostSwitcherInterceptor(private val dummyOkHttpClient: OkHttpClient) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request())
        } catch (e: UnknownHostException) {
            val newHost = getFallbackHost()

            if (chain.request().url.toString() == newHost) throw e

            val newRequest = chain.request().newBuilder()
                .url(newHost)
                .build()
            return chain.proceed(newRequest)
        }
    }

    private fun getFallbackHost(): String {
        dummyOkHttpClient.newCall(Request.Builder().url(FALLBACK_HOST_URL).build()).execute().use { response ->
            return response.body?.string() ?: error("Get fallback hosts error: empty body")
        }
    }

    private companion object {
        const val FALLBACK_HOST_URL = "https://apphud.blob.core.windows.net/apphud-gateway/fallback.txt"
    }
}