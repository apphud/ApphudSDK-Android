package com.apphud.sdk.client

import com.apphud.sdk.ApiKey
import java.lang.reflect.Type

data class RequestConfig(
    val path: String,
    val type: Type,
    val requestType: RequestType,
    val apiKey: ApiKey,
    val queries: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap()
)