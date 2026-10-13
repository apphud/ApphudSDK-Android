package com.apphud.sdk.body

import com.google.gson.annotations.SerializedName

internal data class UserPropertiesBody(
    @SerializedName("installation_id")
    val installationId: String,
    @SerializedName("anonymous_id")
    val anonymousId: String,
    val properties: List<Map<String, Any?>>,
    val force: Boolean
)
