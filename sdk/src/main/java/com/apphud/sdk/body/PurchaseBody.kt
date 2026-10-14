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
    /** `true` only from the restore the user asked for: it moves another customer's purchase here. */
    val restore: Boolean = false,
    /** The old SDK's `device_id` (an update in place, or its data restored), until a registration carrying it succeeds. */
    @SerializedName("legacy_device_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val legacyDeviceId: String? = null,
    val purchases: List<PurchaseItemBody>,
    @SerializedName("package_name")
    val packageName: String? = null
)
