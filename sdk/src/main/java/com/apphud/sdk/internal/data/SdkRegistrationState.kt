package com.apphud.sdk.internal.data

internal class SdkRegistrationState(
    observerMode: Boolean,
) {
    @Volatile
    private var _observerMode: Boolean = observerMode
    var observerMode: Boolean
        get() = _observerMode
        set(value) { _observerMode = value }

    @Volatile
    private var _isRegisteringUser: Boolean = false
    var isRegisteringUser: Boolean
        get() = _isRegisteringUser
        set(value) { _isRegisteringUser = value }

    @Volatile
    private var _hasRespondedToPaywallsRequest: Boolean = false
    var hasRespondedToPaywallsRequest: Boolean
        get() = _hasRespondedToPaywallsRequest
        set(value) { _hasRespondedToPaywallsRequest = value }

    @Volatile
    private var _didRegisterCustomerAtThisLaunch: Boolean = false
    var didRegisterCustomerAtThisLaunch: Boolean
        get() = _didRegisterCustomerAtThisLaunch
        set(value) { _didRegisterCustomerAtThisLaunch = value }

    @Volatile
    private var _deferPlacements: Boolean = false
    var deferPlacements: Boolean
        get() = _deferPlacements
        set(value) { _deferPlacements = value }
}
