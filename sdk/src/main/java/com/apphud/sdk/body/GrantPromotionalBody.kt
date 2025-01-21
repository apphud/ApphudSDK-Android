package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal class GrantPromotionalBody(
    val duration: Int,
    @SerializedName("user_id")
    val userId: String?,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("product_id")
    val productId: String?,
    @SerializedName("product_group_id")
    val productGroupId: String?,
)
