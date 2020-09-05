package com.apphud.sdk.parser

import java.lang.reflect.Type

class JsonParser : Parser {

    override fun <T> toJson(body: T): String = when (body) {
        is Converter<*> -> body.toJson()
        else         -> error("Need use PojoParser")
    }

    override fun <O> fromJson(json: String?, type: Type): O? {
        return when (type) {
            is Converter<*> -> type.fromJson(json) as O?
            else         -> null
        }
    }

    override fun <O> fromJson(json: String?, clas: Class<*>): O? = when (clas) {
        is Converter<*> -> clas.fromJson(json) as O
        else         -> null
    }
}