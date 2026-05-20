package com.apphud.sdk.internal.domain.model

internal data class SyncedDeviceIdentifiers(
    val userId: String,
    val identifiers: DeviceIdentifiers,
)
