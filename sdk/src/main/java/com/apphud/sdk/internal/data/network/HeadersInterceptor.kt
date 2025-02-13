package com.apphud.sdk.internal.data.network

import com.apphud.sdk.BuildConfig
import com.apphud.sdk.internal.domain.model.ApiKey
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID

internal class HeadersInterceptor(private val apiKey: ApiKey) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val userAgentRequest: Request =
            chain.request()
                .newBuilder()
                .header("User-Agent", "Apphud Android ($X_SDK $X_SDK_VERSION)")
                .header("Authorization", "Bearer ${apiKey.value}")
                .header("Accept", "application/json; utf-8")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Platform", "android")
                .header("X-Store", "play_store")
                .header("X-SDK", X_SDK)
                .header("X-SDK-VERSION", X_SDK_VERSION)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .build()

        return chain.proceed(userAgentRequest)
    }

    companion object {
        const val X_SDK_VERSION: String = BuildConfig.VERSION_NAME
        const val X_SDK: String = "Kotlin"
    }
}
