package ru.rosbank.mbdg.myapplication.body

data class AttributionBody(
    val device_id: String,
    val adjust_data: AdjustData? = null,
    val appsflyer_data: AppsflyerData? = null,
    val appsflyer_id: String? = null,
    val facebook_data: FacebookData? = null
)