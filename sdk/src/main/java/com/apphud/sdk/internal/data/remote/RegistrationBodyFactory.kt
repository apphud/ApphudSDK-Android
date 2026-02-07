package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudError
import com.apphud.sdk.UserId
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.internal.provider.RegistrationProvider

internal class RegistrationBodyFactory(
    private val registrationProvider: RegistrationProvider,
) {

    fun create(
        needPaywalls: Boolean,
        isNew: Boolean,
        userId: UserId? = null,
        email: String? = null,
    ): RegistrationBody =
        RegistrationBody(
            locale = registrationProvider.getLocale(),
            sdkVersion = registrationProvider.getSdkVersion(),
            appVersion = registrationProvider.getAppVersion(),
            deviceFamily = registrationProvider.getDeviceFamily(),
            platform = registrationProvider.getPlatform(),
            deviceType = registrationProvider.getDeviceType(),
            osVersion = registrationProvider.getOsVersion(),
            startAppVersion = registrationProvider.getStartAppVersion(),
            idfv = registrationProvider.getIdfv(),
            idfa = registrationProvider.getIdfa(),
            androidId = registrationProvider.getAndroidId(),
            userId = userId ?: registrationProvider.getUserId(),
            deviceId = registrationProvider.getDeviceId() ?: throw ApphudError("SDK not initialized"),
            timeZone = registrationProvider.getTimeZone(),
            isSandbox = registrationProvider.isSandbox(),
            isNew = isNew,
            needPaywalls = needPaywalls,
            needPlacements = needPaywalls,
            firstSeen = registrationProvider.getFirstSeen(),
            sdkLaunchedAt = registrationProvider.getSdkLaunchedAt(),
            requestTime = registrationProvider.getRequestTime(),
            installSource = registrationProvider.getInstallSource(),
            observerMode = registrationProvider.getObserverMode(),
            fromWeb2web = registrationProvider.getFromWeb2Web(),
            email = email
        )
}
