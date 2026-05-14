package com.apphud.sdk.internal.data.dto

import com.google.gson.annotations.SerializedName

internal data class CustomerDto(
    @SerializedName("user_id")
    val userId: String,
    val subscriptions: List<SubscriptionDto>,
    val currency: CurrencyDto?,
    val placements: List<ApphudPlacementDto>?,
    @SerializedName("id")
    val internalId: String?,
    @SerializedName("total_devices_count")
    val totalDevicesCount: Int?,
    val scheme: SchemeDto?,
)

internal data class SchemeDto(
    val name: String?,
    @SerializedName("variation_name")
    val variationName: String?,
    val experiment: ExperimentDto?,
    @SerializedName("remote_config")
    val remoteConfig: String?,
)

internal data class ExperimentDto(
    val name: String?,
)
