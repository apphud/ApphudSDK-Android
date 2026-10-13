package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudError
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.internal.provider.RegistrationProvider

internal class RegistrationBodyFactory(
    private val registrationProvider: RegistrationProvider,
) {

    /**
     * [legacyWeb2Web]: a web-to-web match by email, which the backend accepts only in the old shape:
     * `device_id` with the installation id and the customer's `user_id`, no `anonymous_id`.
     */
    fun create(
        needPlacements: Boolean,
        isNew: Boolean,
        email: String? = null,
        legacyWeb2Web: Boolean = false,
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
            userId = if (legacyWeb2Web) registrationProvider.getServerUserId() else identity.userId,
            installationId = identity.installationId.takeUnless { legacyWeb2Web },
            anonymousId = identity.anonymousId.takeUnless { legacyWeb2Web },
            deviceId = identity.installationId.takeIf { legacyWeb2Web },
            timeZone = registrationProvider.getTimeZone(),
            isSandbox = registrationProvider.isSandbox(),
            isNew = isNew,
            needPlacements = needPlacements,
            firstSeen = registrationProvider.getFirstSeen(),
            sdkLaunchedAt = registrationProvider.getSdkLaunchedAt(),
            requestTime = registrationProvider.getRequestTime(),
            installSource = registrationProvider.getInstallSource(),
            observerMode = registrationProvider.getObserverMode(),
            fromWeb2web = true.takeIf { legacyWeb2Web },
            email = email,
            reinstall = registrationProvider.getReinstall(),
        )
    }
}
