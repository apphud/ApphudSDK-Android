package com.apphud.sdk.storage

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.*

interface Storage {
    var lastRegistration: Long
    var userId: String?
    var deviceId: String?
    var customer: Customer?
    var advertisingId: String?
    var isNeedSync: Boolean
    var facebook: FacebookInfo?
    var firebase: String?
    var appsflyer: AppsflyerInfo?
    var productGroups: List<ApphudGroup>?
    var paywalls: List<ApphudPaywall>?
    var skuDetails: List<SkuDetails>?
}