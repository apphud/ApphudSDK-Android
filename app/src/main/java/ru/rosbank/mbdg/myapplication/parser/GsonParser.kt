package ru.rosbank.mbdg.myapplication.parser

import com.google.gson.Gson
import com.google.gson.JsonParseException
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto

class GsonParser(private val gson: Gson) : Parser {

    override fun <T> toJson(body: T): String = gson.toJson(body)
    override fun <O> fromJson(json: String?): O? = try {
        gson.fromJson<O>(json, ResponseDto::class.java)
    } catch (e: JsonParseException) {
        null
    }
}