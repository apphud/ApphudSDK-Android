package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class PurchaseBody(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    val purchases: List<PurchaseItemBody>,
    @SerializedName("package_name")
    val packageName: String? = null
)
