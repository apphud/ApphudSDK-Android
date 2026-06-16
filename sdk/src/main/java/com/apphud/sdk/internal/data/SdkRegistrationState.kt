package com.apphud.sdk.internal.data

internal class SdkRegistrationState(
    observerMode: Boolean,
) {
    @Volatile var observerMode: Boolean = observerMode
    @Volatile var isRegisteringUser: Boolean = false
    @Volatile var hasRespondedToPaywallsRequest: Boolean = false
    @Volatile var didRegisterCustomerAtThisLaunch: Boolean = false
    @Volatile var deferPlacements: Boolean = false
}
