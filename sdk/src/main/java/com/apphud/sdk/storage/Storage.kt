package com.apphud.sdk.storage

import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.domain.AdjustInfo
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.AppsflyerInfo
import com.apphud.sdk.domain.FacebookInfo

internal interface Storage {
    var lastRegistration: Long
    var userId: String?
    /** Old SDK versions only: read once on upgrade, then removed. */
    var deviceId: String?
    /**
     * The old version's generated device id, kept for good after an update: apps that saved it as their
     * user id pass it back, and it is never a user. Logout keeps it.
     */
    var oldDeviceId: String?
    var anonymousId: String?

    /** Only a user id the app set by identify. */
    var identifiedUserId: String?

    /** Set while the backend hasn't seen the current ids; cleared by a successful registration. */
    var identitySyncPending: Boolean
    var apphudUser: ApphudUser?
    var isNeedSync: Boolean
    var facebook: FacebookInfo?
    var firebase: String?
    var appsflyer: AppsflyerInfo?
    var adjust: AdjustInfo?
    var productGroups: List<ApphudGroup>?
    var productDetails: List<String>?
    var properties: HashMap<String, ApphudUserProperty>?
    var deviceIdentifiers: Array<String>
    var syncedDeviceIdentifiersUserId: String?
    var syncedDeviceIdentifiers: Array<String>?
    var cacheVersion: String?
    var connectDomainUrl: String?
    var submittedPushToken: String?
}
