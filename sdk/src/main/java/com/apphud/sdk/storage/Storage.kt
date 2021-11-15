package com.apphud.sdk.storage

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.*

interface Storage {
    var userId: String?
    var deviceId: String?
    var customer: Customer?
    var advertisingId: String?
    var isNeedSync: Boolean
    var facebook: FacebookInfo?
    var firebase: String?
    var appsflyer: AppsflyerInfo?
    var paywalls: List<ApphudPaywall>?
    var productGroups: List<ApphudGroup>?
    var lastRegistration: Long
    var skuDetails: List<SkuDetails>?
}