package com.apphud.app.ui.storage

import com.apphud.app.ui.managers.Integration
import com.apphud.sdk.domain.*

interface Storage {
    var userId: String?
    var apiKey: String?
    var host: String?
    var sandbox: String?
    var username: String?
    var showEmptyPaywalls: Boolean
    var showEmptyGroups: Boolean
    var integrations: HashMap<Integration, Boolean>
}