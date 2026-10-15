package com.apphud.sdk.body

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

internal data class UserPropertiesBody(
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
    val properties: List<Map<String, Any?>>,
    val force: Boolean
)
