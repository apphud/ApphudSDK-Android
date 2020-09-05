package com.apphud.sdk.parser

interface Converter<T> {
    fun toJson(): String
    fun fromJson(json: String?): T?
}