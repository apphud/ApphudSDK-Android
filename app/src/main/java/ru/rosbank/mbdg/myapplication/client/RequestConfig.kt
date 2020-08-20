package ru.rosbank.mbdg.myapplication.client

import java.lang.reflect.Type

data class RequestConfig(
    val path: String,
    val type: Type,
    val requestType: RequestType,
    val headers: Map<String, String> = emptyMap()
)