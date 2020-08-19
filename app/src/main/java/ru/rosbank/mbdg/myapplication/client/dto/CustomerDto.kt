package ru.rosbank.mbdg.myapplication.client.dto

data class CustomerDto(
    val id: String,
    val user_id: String,
    val locale: String,
    val created_at: String,
    val subscriptions: String,
    val currency: CurrencyDto,
    val devices: List<DeviceDto>
)