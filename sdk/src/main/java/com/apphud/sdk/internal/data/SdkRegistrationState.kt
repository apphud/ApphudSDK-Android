package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudUser

internal class SdkRegistrationState(
    observerMode: Boolean,
) {
    @Volatile
    private var _observerMode: Boolean = observerMode
    val observerMode: Boolean
        get() = _observerMode

    @Volatile
    private var _isRegisteringUser: Boolean = false
    val isRegisteringUser: Boolean
        get() = _isRegisteringUser

    @Volatile
    private var _hasRespondedToPaywallsRequest: Boolean = false
    val hasRespondedToPaywallsRequest: Boolean
        get() = _hasRespondedToPaywallsRequest

    @Volatile
    private var _didRegisterCustomerAtThisLaunch: Boolean = false
    val didRegisterCustomerAtThisLaunch: Boolean
        get() = _didRegisterCustomerAtThisLaunch

    @Volatile
    private var _deferPlacements: Boolean = false
    val deferPlacements: Boolean
        get() = _deferPlacements

    fun setObserverMode(value: Boolean) {
        _observerMode = value
    }

    fun setDeferPlacements(value: Boolean) {
        _deferPlacements = value
    }

    fun markRegistrationStarted() {
        _isRegisteringUser = true
    }

    fun markRegistrationFinished() {
        _isRegisteringUser = false
    }

    fun markCustomerRegisteredAtThisLaunch(value: Boolean) {
        _didRegisterCustomerAtThisLaunch = value
    }

    fun markPaywallsResponded(value: Boolean) {
        _hasRespondedToPaywallsRequest = value
    }

    fun markPaywallsRespondedForUser(user: ApphudUser) {
        _hasRespondedToPaywallsRequest =
            _hasRespondedToPaywallsRequest || user.placements.isNotEmpty() || observerMode
    }
}
