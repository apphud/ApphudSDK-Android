package com.apphud.sdk.storage

import com.apphud.sdk.domain.Customer

interface Storage {
    var userId: String?
    var deviceId: String?
    var customer: Customer?
    var advertisingId: String?
    var isNeedSync: Boolean
}