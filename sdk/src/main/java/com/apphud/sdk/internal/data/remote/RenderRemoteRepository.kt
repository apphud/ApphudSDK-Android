package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.domain.RenderResult
import com.apphud.sdk.internal.data.mapper.RenderResultMapper
import com.apphud.sdk.internal.domain.model.RenderItem
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

internal class RenderRemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val renderResultMapper: RenderResultMapper,
) {

    suspend fun renderPaywallProperties(
        items: List<RenderItem>,
    ): Result<RenderResult> =
        runCatchingCancellable {
            val requestBody = RenderPropertiesRequest(items)
            val request = buildPostRequest(RENDER_PROPERTIES_URL, requestBody)
            executeForResponse<List<Map<String, Any>>>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to render paywall properties"
                throw ApphudError(message, null, APPHUD_ERROR_NO_INTERNET, e)
            }
            .mapCatching { responseDto ->
                val results = responseDto.data.results ?: throw ApphudError("Empty render response")
                renderResultMapper.toDomain(results)
            }

    private data class RenderPropertiesRequest(
        val items: List<RenderItem>,
    )

    private companion object {
        val RENDER_PROPERTIES_URL = "https://gateway.apphud.com/v2/paywall_configs/items/render_properties".toHttpUrl()
    }
}

