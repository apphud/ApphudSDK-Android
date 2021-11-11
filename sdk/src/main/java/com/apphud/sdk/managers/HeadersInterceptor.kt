package com.apphud.sdk.managers

import com.apphud.sdk.BuildConfig
import com.apphud.sdk.client.ApiClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class HeadersInterceptor(private val apiKey: String?) : Interceptor {
    companion object Shared{
        const val X_SDK_VERSION: String = BuildConfig.VERSION_NAME
        var X_SDK: String = "android"
        var HOST: String = ApiClient.host
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val userAgentRequest: Request = chain.request()
            .newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json; utf-8")
            .header("Content-Type", "application/json; utf-8")
            .header("X-Platform", "android")
            .header("X-SDK", X_SDK)
            .header("X-SDK-VERSION", X_SDK_VERSION)
            .build()

        return chain.proceed(userAgentRequest)
    }
}