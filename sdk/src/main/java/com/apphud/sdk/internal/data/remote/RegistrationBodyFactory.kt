package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudError
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.internal.provider.RegistrationProvider

internal class RegistrationBodyFactory(
    private val registrationProvider: RegistrationProvider,
) {

    /** [fromWeb2Web]: a web-to-web match by [email]; the ids are sent as on any registration. */
    fun create(
        needPlacements: Boolean,
        isNew: Boolean,
        email: String? = null,
        fromWeb2Web: Boolean = false,
    ): RegistrationBody {
        val identity = registrationProvider.getIdentity() ?: throw ApphudError("SDK not initialized")
        return RegistrationBody(
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
            userId = identity.userId,
            installationId = identity.installationId,
            anonymousId = identity.anonymousId,
            legacyDeviceId = registrationProvider.getLegacyDeviceId(),
            timeZone = registrationProvider.getTimeZone(),
            isSandbox = registrationProvider.isSandbox(),
            isNew = isNew,
            needPlacements = needPlacements,
            firstSeen = registrationProvider.getFirstSeen(),
            sdkLaunchedAt = registrationProvider.getSdkLaunchedAt(),
            requestTime = registrationProvider.getRequestTime(),
            installSource = registrationProvider.getInstallSource(),
            observerMode = registrationProvider.getObserverMode(),
            fromWeb2web = true.takeIf { fromWeb2Web },
            email = email,
            reinstall = registrationProvider.getReinstall(),
        )
    }
}
