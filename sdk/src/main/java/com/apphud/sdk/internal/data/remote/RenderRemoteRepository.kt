package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.RenderResult
import com.apphud.sdk.internal.data.mapper.RenderResultMapper
import com.apphud.sdk.internal.domain.model.RenderItem
import com.apphud.sdk.internal.ApphudDispatchers
import com.apphud.sdk.internal.data.network.UrlProvider
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.google.gson.Gson
import okhttp3.OkHttpClient

internal class RenderRemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val renderResultMapper: RenderResultMapper,
    private val urlProvider: UrlProvider,
    private val dispatchers: ApphudDispatchers,
) {

    suspend fun renderPaywallProperties(
        items: List<RenderItem>,
    ): Result<RenderResult> =
        runCatchingCancellable {
            val requestBody = RenderPropertiesRequest(items)
            val request = buildPostRequest(urlProvider.renderPropertiesUrl, requestBody)
            executeForResponse<List<Map<String, Any>>>(okHttpClient, gson, request, dispatchers.io)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to render paywall properties"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { responseDto ->
                val results = responseDto.data.results ?: throw ApphudError("Empty render response")
                renderResultMapper.toDomain(results)
            }

    private data class RenderPropertiesRequest(
        val items: List<RenderItem>,
    )
}

