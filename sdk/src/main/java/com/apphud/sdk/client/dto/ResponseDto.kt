package com.apphud.sdk.client.dto

internal data class ResponseDto<T>(
    val data: DataDto<T>,
    val errors: List<Any>?,
)
