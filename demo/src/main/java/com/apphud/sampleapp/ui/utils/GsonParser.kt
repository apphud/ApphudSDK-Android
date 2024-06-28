package com.apphud.sampleapp.ui.utils

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

class GsonParser(private val gson: Gson) : Parser {

    override fun <T> toJson(body: T): String = gson.toJson(body)
    override fun <O> fromJson(json: String?, type: Type): O? = try {
        gson.fromJson<O>(json, type)
    } catch (e: JsonParseException) {
        null
    }

    override fun isJson(jsonString: String?): Boolean {
        return try {
            gson.fromJson(jsonString, Any::class.java)
            true
        } catch (e: JsonSyntaxException) {
            false
        }
    }
}