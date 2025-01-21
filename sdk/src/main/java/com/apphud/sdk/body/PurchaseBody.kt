package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class PurchaseBody(
    @SerializedName("device_id")
    val deviceId: String,
    val purchases: List<Any>,
)
