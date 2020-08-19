package ru.rosbank.mbdg.myapplication.body

data class AttributionBody(
    val device_id: String,
    val adjust_data: Map<String, String>?,
    val appsflyer_data: Map<String, String>?,
    val appsflyer_id: String?,
    val facebook_data: Map<String, Boolean>
)