package com.apphud.sdk.storage

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
    var productGroups: List<ApphudGroup>?
}