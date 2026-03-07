package com.apphud.sdk.internal.domain.model

internal data class DeviceIdentifiers(
    val advertisingId: String? = null,
    val appSetId: String? = null,
    val androidId: String? = null,
) {
    companion object {
        val EMPTY = DeviceIdentifiers()
    }
}
