package ru.rosbank.mbdg.myapplication.parser

interface Parser {
    fun <T> toJson(body: T): String
    fun <O> fromJson(json: String?): O?
}