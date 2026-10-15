package com.apphud.sdk.body

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal data class PurchaseBody(
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
    /** Only once the user is identified. */
    @SerializedName("user_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val userId: String? = null,
    /**
     * Identity version 2 only: `true` only from the restore the user asked for, which moves another
     * customer's purchase here.
     */
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val restore: Boolean? = null,
    /** The old SDK's `device_id` (an update in place, or its data restored), until a registration carrying it succeeds; not after a logout or a user switch. */
    @SerializedName("legacy_device_id")
    @JsonAdapter(OmitWhenNull::class, nullSafe = false)
    val legacyDeviceId: String? = null,
    val purchases: List<PurchaseItemBody>,
    @SerializedName("package_name")
    val packageName: String? = null
)
