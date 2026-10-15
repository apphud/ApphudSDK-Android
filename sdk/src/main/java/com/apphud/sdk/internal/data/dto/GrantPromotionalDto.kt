package com.apphud.sdk.internal.data.dto

import com.apphud.sdk.body.OmitWhenNull
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal class GrantPromotionalDto(
    val duration: Int,
    /** Only once the user is identified. */
    @SerializedName("user_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val userId: String?,
    /** Identity version 1 only. */
    @SerializedName("device_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val deviceId: String? = null,
    /** Identity version 2 only. */
    @SerializedName("installation_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val installationId: String? = null,
    /** Identity version 2 only. */
    @SerializedName("anonymous_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val anonymousId: String? = null,
    @SerializedName("product_id")
    val productId: String?,
    @SerializedName("product_group_id")
    val productGroupId: String?,
)
