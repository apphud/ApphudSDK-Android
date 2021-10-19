package com.apphud.sdk.managers

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class HeadersInterceptor() : Interceptor {
    companion object Shared{
        var X_SDK: String = "android"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val userAgentRequest: Request = chain.request()
            .newBuilder()
            .header("Accept", "application/json; utf-8")
            .header("Content-Type", "application/json; utf-8")
            .header("X-Platform", "android")
            .header("X-Platform", "android")
            .header("X-SDK", X_SDK)
            .build()
        return chain.proceed(userAgentRequest)
    }
}