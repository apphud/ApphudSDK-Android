package com.apphud.sdk.managers

import com.apphud.sdk.BuildConfig
import com.apphud.sdk.client.ApiClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID

@Deprecated("")
class LegacyHeadersInterceptor(private val apiKey: String?) : Interceptor {
    companion object Shared {
        var X_SDK_VERSION: String = BuildConfig.VERSION_NAME
        var X_SDK: String = "Kotlin"
        val HOST: String get() = ApiClient.host
        var isBlocked: Boolean = false
    }

    val uuidKey = UUID.randomUUID().toString()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val userAgentRequest: Request =
            chain.request()
                .newBuilder()
                .header("User-Agent", "Apphud Android ($X_SDK $X_SDK_VERSION)")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json; utf-8")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Platform", "android")
                .header("X-Store", "play_store")
                .header("X-SDK", X_SDK)
                .header("X-SDK-VERSION", X_SDK_VERSION)
                .header("Idempotency-Key", uuidKey)
                .build()

        return chain.proceed(userAgentRequest)
    }
}
