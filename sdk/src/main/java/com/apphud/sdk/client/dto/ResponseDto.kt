package com.apphud.sdk.client.dto

data class ResponseDto<T>(
    val data: DataDto<T>,
    val errors: List<Any>?
)