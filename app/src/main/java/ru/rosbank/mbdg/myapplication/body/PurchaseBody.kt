package ru.rosbank.mbdg.myapplication.body

data class PurchaseBody(
    val device_id: String,
    val purchases: List<PurchaseItemBody>
)