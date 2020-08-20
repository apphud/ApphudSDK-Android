package ru.rosbank.mbdg.myapplication.parser

import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GsonParser(private val gson: Gson) : Parser {

    override fun <T> toJson(body: T): String = gson.toJson(body)
    override fun <O> fromJson(json: String?, type: Type): O? = try {
        gson.fromJson<O>(json, type)
    } catch (e: JsonParseException) {
        null
    }
}