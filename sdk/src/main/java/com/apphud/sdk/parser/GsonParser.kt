package com.apphud.sdk.parser

import com.apphud.sdk.client.dto.ResponseDto
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class GsonParser(private val gson: Gson) : Parser {

    override fun <T> toJson(body: T): String = gson.toJson(body)
    override fun <O> fromJson(json: String?, type: Type): O? = try {
        gson.fromJson<O>(json, type)
    } catch (e: JsonParseException) {
        null
    }

    override fun <O> fromJson(json: String?, clas: Class<*>): O? = try {
//        val type = object : TypeToken<ResponseDto<O>>(){}.type
        //ResponseDto::class.java.typeParameters
        clas.typeParameters
        val parse = gson.fromJson<O>(json, ResponseDto::class.java)
        parse
    } catch (e: JsonParseException){
        null
    }
}