package com.apphud.sdk.internal.data.network

import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

internal class CustomerIdInterceptor(
    private val customerIdProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val customerId = customerIdProvider()?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(chain.request())

        val request = chain.request()
        val enrichedRequest = when (request.method) {
            "GET" -> enrichGetRequest(request, customerId)
            "POST", "PUT" -> enrichBodyRequest(request, customerId)
            else -> request
        }

        return chain.proceed(enrichedRequest)
    }

    private fun enrichGetRequest(request: Request, customerId: String): Request {
        val url = request.url.newBuilder()
            .setQueryParameter(CUSTOMER_ID_FIELD, customerId)
            .build()
        return request.newBuilder().url(url).build()
    }

    private fun enrichBodyRequest(request: Request, customerId: String): Request {
        val body = request.body ?: return request
        val contentType = body.contentType()
        val contentTypeString = contentType?.toString().orEmpty()
        if (contentTypeString.isNotEmpty() && !contentTypeString.contains("json", ignoreCase = true)) {
            return request
        }

        val buffer = Buffer()
        return runCatching {
            body.writeTo(buffer)
            val bodyString = buffer.readUtf8()
            if (bodyString.isBlank()) return request

            val jsonElement = JsonParser.parseString(bodyString)
            if (!jsonElement.isJsonObject) return request

            val jsonObject = jsonElement.asJsonObject
            if (jsonObject.has(CUSTOMER_ID_FIELD)) return request

            jsonObject.addProperty(CUSTOMER_ID_FIELD, customerId)
            val mediaType = contentType ?: "application/json; charset=utf-8".toMediaTypeOrNull()
            val newBody = jsonObject.toString().toRequestBody(mediaType)
            request.newBuilder().method(request.method, newBody).build()
        }.getOrDefault(request)
    }

    private companion object {
        const val CUSTOMER_ID_FIELD = "customer_id"
    }
}
