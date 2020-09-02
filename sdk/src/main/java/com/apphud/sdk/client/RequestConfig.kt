package com.apphud.sdk.client

import java.lang.reflect.Type

data class RequestConfig(
    val path: String,
    val type: Type,
    val requestType: RequestType,
    val queries: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap()
)