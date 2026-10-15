package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudError
import com.apphud.sdk.UserId
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.internal.provider.RegistrationProvider

internal class RegistrationBodyFactory(
    private val registrationProvider: RegistrationProvider,
) {

    /**
     * [fromWeb2Web]: a web-to-web match by [email]; the ids are sent as on any registration. Identity
     * version 1 sends `device_id`, [userId] when given, and `from_web2web` on every registration
     * after a web-to-web attribution, as SDK 3.6 did.
     */
    fun create(
        needPlacements: Boolean,
        isNew: Boolean,
        email: String? = null,
        fromWeb2Web: Boolean = false,
        userId: UserId? = null,
    ): RegistrationBody {
        val ids = registrationProvider.getRequestIds() ?: throw ApphudError("SDK not initialized")
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
            userId = userId ?: ids.userId,
            deviceId = ids.deviceId,
            installationId = ids.installationId,
            anonymousId = ids.anonymousId,
            legacyDeviceId = registrationProvider.getLegacyDeviceId().takeUnless { ids.isVersion1 },
            timeZone = registrationProvider.getTimeZone(),
            isSandbox = registrationProvider.isSandbox(),
            isNew = isNew,
            needPlacements = needPlacements,
            firstSeen = registrationProvider.getFirstSeen(),
            sdkLaunchedAt = registrationProvider.getSdkLaunchedAt(),
            requestTime = registrationProvider.getRequestTime(),
            installSource = registrationProvider.getInstallSource(),
            observerMode = registrationProvider.getObserverMode(),
            fromWeb2web = if (ids.isVersion1) registrationProvider.getFromWeb2Web() else true.takeIf { fromWeb2Web },
            email = email,
            reinstall = registrationProvider.getReinstall().takeUnless { ids.isVersion1 },
        )
    }
}
