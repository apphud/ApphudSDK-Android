package ru.rosbank.mbdg.myapplication.body

data class PushBody(
    val device_id: String,
    val push_token: String
)