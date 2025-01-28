package com.apphud.sdk

import com.apphud.sdk.body.RegistrationBody

internal fun mkRegistrationBody(
    userId: String,
    deviceId: String,
) = RegistrationBody(
    locale = "ru_RU",
    sdkVersion = "1.0",
    appVersion = "1.0.0",
    deviceFamily = "Android",
    platform = "Android",
    deviceType = "DEVICE_TYPE",
    osVersion = "6.0.1",
    startAppVersion = "1.0",
    idfv = "11112222",
    idfa = "22221111",
    userId = userId,
    deviceId = deviceId,
    timeZone = "UTF",
    isSandbox = true,
    isNew = true,
    needPaywalls = true,
    needPlacements = true,
    androidId = "",
    firstSeen = null,
)
