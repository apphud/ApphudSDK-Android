package com.apphud.sdk.internal.data.network

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ApphudUtils
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * HTTP logging interceptor that logs requests and responses with pretty-printed JSON bodies.
 *
 * Each request-response pair is tagged with a unique 8-character traceId (e.g., `[a1b2c3d4]`)
 * to easily correlate requests with their corresponding responses in logs.
 *
 * Example output:
 * ```
 * [a1b2c3d4] Start POST request https://api.apphud.com/v2/customers with params:
 * {
 *   "device_id": "xxx"
 * }
 *
 * [a1b2c3d4] Finished POST request https://api.apphud.com/v2/customers with response: 200
 * {
 *   "data": {...}
 * }
 * ```
 */
internal class PrettyHttpLoggingInterceptor(
    private val prettyJsonFormatter: PrettyJsonFormatter
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!ApphudUtils.httpLogging) {
            return chain.proceed(request)
        }

        val traceId = UUID.randomUUID().toString().take(8)
        logRequestStart(request, traceId)
        val response = chain.proceed(request)
        return logResponse(request, response, traceId)
    }

    private fun logRequestStart(request: okhttp3.Request, traceId: String) {
        val method = request.method
        val url = request.url

        val requestBody = request.body
        val bodyString = if (requestBody != null) {
            Buffer().use { buffer ->
                requestBody.writeTo(buffer)
                buffer.readString(StandardCharsets.UTF_8)
            }
        } else {
            null
        }

        val prettyBody = prettyJsonFormatter.format(bodyString)
        val bodyPart = if (prettyBody != null) " with params:\n$prettyBody" else ""

        ApphudLog.logI("[$traceId] Start $method request $url$bodyPart")
    }

    private fun logResponse(request: okhttp3.Request, response: Response, traceId: String): Response {
        val method = request.method
        val url = request.url
        val code = response.code

        val responseBody = response.body
        val source = responseBody?.source()
        source?.request(Long.MAX_VALUE)
        val bodyString = source?.buffer?.clone()?.use { clonedBuffer ->
            clonedBuffer.readString(StandardCharsets.UTF_8)
        }

        val prettyBody = prettyJsonFormatter.format(bodyString)
        val bodyPart = if (prettyBody != null) "\n$prettyBody" else ""

        ApphudLog.logI("[$traceId] Finished $method request $url with response: $code$bodyPart")

        return response
    }
}
