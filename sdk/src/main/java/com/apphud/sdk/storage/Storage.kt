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
    /**
     * Identity version 1's device id (and old SDK versions'). Kept while version 2 runs, so version 1
     * can come back to the same customer; a logout on either version removes it.
     */
    var deviceId: String?
    /**
     * The old version's generated device id, kept for good after an update: apps that saved it as their
     * user id pass it back, and it is never a user. Logout keeps it.
     */
    var oldDeviceId: String?
    /** Further SDK-generated legacy ids from later V1 migrations; kept across logout. */
    var additionalOldDeviceIds: Set<String>
    var anonymousId: String?

    /** Only a user id the app set by identify. */
    var identifiedUserId: String?

    /** Set while the backend hasn't seen the current ids; cleared by a successful registration. */
    var identitySyncPending: Boolean

    /**
     * The identity version that ran last on the ids these preferences hold, "V1" or "V2". Auto Backup
     * restores it with them, unlike the install's own version in `noBackupFilesDir`.
     */
    var idsIdentityVersion: String?

    /** Set by a logout on version 2: version 1's device id belonged to the person logged out. */
    var version1IdsReset: Boolean

    /**
     * Set by a logout on version 1: the next start on version 2 takes its new person, and the old ids of
     * version 1 belong to the person logged out.
     */
    var version1LoggedOut: Boolean

    /**
     * Set when version 1 takes the ids of version 2 over, until a registration of version 1 succeeds: the
     * backend has to see its device id.
     */
    var version1RegistrationPending: Boolean
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
