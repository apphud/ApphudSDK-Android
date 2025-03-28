package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.dto.ResponseDto
import com.apphud.sdk.managers.RequestManager.parser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody


internal fun buildPostRequest(
    url: HttpUrl,
    params: Any,
): Request {
    val json = parser.toJson(params)
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val requestBody = json.toRequestBody(mediaType)

    val request = Request.Builder()
    return request.url(url)
        .post(requestBody)
        .build()
}

internal fun buildGetRequest(url: HttpUrl, params: Map<String, String>): Request {
    val httpUrl = url.newBuilder().apply {
        params.forEach { (key, value) ->
            addQueryParameter(key, value)
        }
    }.build()

    val request = Request.Builder()
    return request.url(httpUrl)
        .get()
        .build()
}

internal suspend inline fun <reified T> executeForResponse(
    okHttpClient: OkHttpClient,
    gson: Gson,
    request: Request,
): ResponseDto<T> =
    withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message =
                    "finish ${request.method} request ${request.url} " +
                            "failed with code: ${response.code} response: ${
                                response.body?.string()
                            }"
                ApphudLog.logE(message)
                error(message)
            }
            val json = response.body?.string() ?: error(
                "finish ${request.method} request ${request.url} with empty body"
            )
            val type = object : TypeToken<ResponseDto<T>>() {}.type
            gson.fromJson(json, type)
        }
    }