package com.apphud.sdk.domain

import com.google.gson.annotations.SerializedName

internal class AdjustInfo(
    val adid: String?,
    @SerializedName("adjust_data")
    val adjustData: Map<String, Any>?,
)
