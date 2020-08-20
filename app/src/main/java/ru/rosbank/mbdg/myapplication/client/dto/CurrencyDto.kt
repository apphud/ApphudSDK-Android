package ru.rosbank.mbdg.myapplication.client.dto

data class CurrencyDto(
    val id: String,
    val code: String?,
    val country_code: String
)