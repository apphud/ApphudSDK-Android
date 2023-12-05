package com.apphud.sdk.storage

import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.domain.*

interface Storage {
    var lastRegistration: Long
    var userId: String?
    var deviceId: String?
    var apphudUser: ApphudUser?
    var advertisingId: String?
    var isNeedSync: Boolean
    var facebook: FacebookInfo?
    var firebase: String?
    var appsflyer: AppsflyerInfo?
    var adjust: AdjustInfo?
    var productGroups: List<ApphudGroup>?
    var paywalls: List<ApphudPaywall>?
    var placements: List<ApphudPlacement>?
    var productDetails: List<String>?
    var properties: HashMap<String, ApphudUserProperty>?
}
