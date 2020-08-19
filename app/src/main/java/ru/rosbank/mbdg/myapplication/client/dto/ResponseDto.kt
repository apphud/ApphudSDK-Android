package ru.rosbank.mbdg.myapplication.client.dto

data class ResponseDto<T>(
    val data: DataDto<T>,
    val errors: List<Any>?
)