package ru.rosbank.mbdg.myapplication.parser

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken

class GsonParser(private val gson: Gson) : Parser {

    override fun <T> toJson(body: T): String = gson.toJson(body)
    override fun <O> fromJson(json: String?): O? = try {
        gson.fromJson(json, object : TypeToken<O> () {}.type)
    } catch (e: JsonParseException) {
        null
    }
}