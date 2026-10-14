package com.apphud.sdk.body

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal data class PurchaseBody(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    /** Only once the user is identified. */
    @SerializedName("user_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val userId: String? = null,
    val purchases: List<PurchaseItemBody>,
    @SerializedName("package_name")
    val packageName: String? = null
)
