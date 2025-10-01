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
                .header("User-Agent", "Apphud Android (${SdkHeaders.X_SDK} ${SdkHeaders.X_SDK_VERSION})")
                .header("Authorization", "Bearer ${apiKey.value}")
                .header("Accept", "application/json; utf-8")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Platform", "android")
                .header("X-Store", "play_store")
                .header("X-SDK", SdkHeaders.X_SDK)
                .header("X-SDK-VERSION", SdkHeaders.X_SDK_VERSION)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .build()

        return chain.proceed(userAgentRequest)
    }
}

/**
 * Public configuration object for SDK headers.
 * Allows customization of SDK identification values used in API requests.
 */
object SdkHeaders {
    /**
     * The SDK version string used in headers.
     * Can be modified to customize the version reported to the API.
     */
    var X_SDK_VERSION: String = BuildConfig.VERSION_NAME

    /**
     * The SDK identifier string used in headers.
     * Can be modified to customize the SDK identifier reported to the API.
     */
    var X_SDK: String = "Kotlin"
}
