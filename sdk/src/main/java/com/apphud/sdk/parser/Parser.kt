package com.apphud.sdk.parser

import java.lang.reflect.Type

interface Parser {
    fun <T> toJson(body: T): String
    fun <O> fromJson(json: String?, type: Type): O?
    fun isJson(jsonString: String?): Boolean
}