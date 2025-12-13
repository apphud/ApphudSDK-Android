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
    var deviceId: String?
    var apphudUser: ApphudUser?
    var deviceIdentifiers: Array<String>
    var isNeedSync: Boolean
    var facebook: FacebookInfo?
    var firebase: String?
    var appsflyer: AppsflyerInfo?
    var adjust: AdjustInfo?
    var productGroups: List<ApphudGroup>?
    var productDetails: List<String>?
    var properties: HashMap<String, ApphudUserProperty>?
    var cacheVersion: String?
}
