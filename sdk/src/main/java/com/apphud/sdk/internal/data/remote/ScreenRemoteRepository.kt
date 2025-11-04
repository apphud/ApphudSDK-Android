package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.domain.model.ApiKey
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

internal class ScreenRemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val apiKey: ApiKey,
) {

    suspend fun loadScreenHtmlData(screenId: String, deviceId: String): Result<String> =
        runCatchingCancellable {
            val paramsMap = mapOf(
                "api_key" to apiKey.value,
                "locale" to Locale.getDefault().toLanguageTag(),
                "device_id" to deviceId,
                "v" to "2"
            )
            val request = buildGetRequest(PREVIEW_SCREEN_URL.newBuilder().addPathSegment(screenId).build(), paramsMap)
                .newBuilder()
                .addHeader("APPHUD-API-KEY", apiKey.value)
                .build()

            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val message = "finish ${request.method} request ${request.url} " +
                            "failed with code: ${response.code} response: $responseBody"
                        ApphudLog.logE(message)
                        error(message)
                    }

                    response.body?.string() ?: error(
                        "finish ${request.method} request ${request.url} with empty body"
                    )
                }
            }
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to load screen HTML"
                throw ApphudError(message, originalCause = e)
            }

    private companion object {
        private const val BASE_URL = "https://gateway.apphud.com"
        val PREVIEW_SCREEN_URL = "$BASE_URL/preview_screen".toHttpUrl()
    }
}