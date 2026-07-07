package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.data.network.UrlProvider
import com.apphud.sdk.internal.domain.mapper.RuleScreenHtmlSanitizer
import com.apphud.sdk.internal.domain.model.ApiKey
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.google.gson.Gson
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale

internal class ScreenRemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val apiKey: ApiKey,
    private val urlProvider: UrlProvider,
    private val dispatchers: ApphudDispatchers,
) {

    suspend fun loadScreenHtmlData(screenId: String, deviceId: String): Result<String> =
        runCatchingCancellable {
            val paramsMap = mapOf(
                "api_key" to apiKey.value,
                "locale" to Locale.getDefault().toLanguageTag(),
                "device_id" to deviceId,
                "v" to "2"
            )
            val request = buildGetRequest(
                urlProvider.previewScreenUrl.newBuilder().addPathSegment(screenId).build(),
                paramsMap,
            )
                .newBuilder()
                .addHeader("APPHUD-API-KEY", apiKey.value)
                .build()

            withContext(dispatchers.io) {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val message = "finish ${request.method} request ${request.url} " +
                            "failed with code: ${response.code} response: $responseBody"
                        ApphudLog.logE(message)
                        error(message)
                    }

                    val html = response.body?.string() ?: error(
                        "finish ${request.method} request ${request.url} with empty body"
                    )
                    RuleScreenHtmlSanitizer.sanitizeForInAppWebView(html)
                }
            }
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to load screen HTML"
                throw ApphudError(message, originalCause = e)
            }
}