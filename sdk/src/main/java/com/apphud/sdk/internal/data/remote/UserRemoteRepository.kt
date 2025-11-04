package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.APPHUD_ERROR_NO_INTERNET
import com.apphud.sdk.ApphudError
import com.apphud.sdk.body.UserPropertiesBody
import com.apphud.sdk.domain.Attribution
import com.apphud.sdk.internal.data.dto.AttributionDto
import com.apphud.sdk.internal.data.dto.ResponseDto
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.mappers.AttributionMapper
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

internal class UserRemoteRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val attributionMapper: AttributionMapper,
) {

    suspend fun setUserProperties(
        userPropertiesBody: UserPropertiesBody,
    ): Result<Attribution> =
        runCatchingCancellable {
            val request = buildPostRequest(PROPERTIES_URL, userPropertiesBody)
            executeForResponse<AttributionDto>(okHttpClient, gson, request)
        }
            .recoverCatching { e ->
                val message = e.message ?: "Failed to send properties"
                throw ApphudError(message, originalCause = e)
            }
            .mapCatching { response: ResponseDto<AttributionDto> ->
                response.data.results?.let {
                    attributionMapper.map(it)
                } ?: throw ApphudError("Failed to send properties")
            }


    private companion object {
        val PROPERTIES_URL = "https://gateway.apphud.com/v1/customers/properties".toHttpUrl()
    }
}