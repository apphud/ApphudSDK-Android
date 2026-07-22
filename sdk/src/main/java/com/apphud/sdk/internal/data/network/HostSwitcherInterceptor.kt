package com.apphud.sdk.internal.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class HostSwitcherInterceptor(
    private val dummyOkHttpClient: OkHttpClient,
    private val urlProvider: UrlProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return try {
            chain.proceed(request)
        } catch (e: UnknownHostException) {
            tryFallbackHost(chain, e)
        } catch (e: SocketTimeoutException) {
            tryFallbackHost(chain, e)
        }
    }

    private fun tryFallbackHost(chain: Interceptor.Chain, originalException: Exception?): Response {
        val chainHost = chain.request().url.host

        if (!isProductionHost(chainHost)) {
            Log.d("ApphudLogs", "Skipping fallback host switch for non-production host: $chainHost")
            originalException?.let { throw it }
            error("Request failed for non-production host: $chainHost")
        }

        val newHost = getFallbackHost().trim()
        val newHostWithoutScheme = newHost.removePrefix("https://")

        if (chainHost == newHostWithoutScheme) {
            // Already using fallback host, let the next interceptor handle it
            originalException?.let { throw it }
            error("Fallback host also failed")
        }

        Log.e("ApphudLogs", "Exception ${originalException}, url: ${chain.request().url}")

        val originalUrl = chain.request().url
        val newUrl = originalUrl.newBuilder()
            .host(newHostWithoutScheme)
            .build()
        
        val newRequest = chain.request().newBuilder()
            .url(newUrl)
            .build()
        
        val response = chain.proceed(newRequest)

        if (response.isSuccessful) {
            Log.d("ApphudLogs", "Switching to fallback host: $newHost")
            urlProvider.updateBaseUrl(newHost)
        } else {
            Log.d("ApphudLogs", "Do not switch to fallback host $newHost")
        }

        return response
    }

    private fun getFallbackHost(): String {
        dummyOkHttpClient.newCall(Request.Builder().url(FALLBACK_HOST_URL).build()).execute().use { response ->
            return response.body?.string() ?: error("Get fallback hosts error: empty body")
        }
    }

    private companion object {
        const val FALLBACK_HOST_URL = "https://apphud.blob.core.windows.net/apphud-gateway/fallback.txt"
        private const val PRODUCTION_DOMAIN = "apphud.com"

        private fun isProductionHost(host: String): Boolean =
            host == PRODUCTION_DOMAIN || host.endsWith(".$PRODUCTION_DOMAIN")
    }
}