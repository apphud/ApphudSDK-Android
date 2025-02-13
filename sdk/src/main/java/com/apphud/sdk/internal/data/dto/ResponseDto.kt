package com.apphud.sdk.internal.data.dto

internal data class ResponseDto<T>(
    val data: DataDto<T>,
    val errors: List<Any>?,
)
