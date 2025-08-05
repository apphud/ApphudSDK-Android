package com.apphud.sdk.domain

import com.apphud.sdk.ApphudError

/**
 * Result returned in the callback of `Apphud.showPaywallScreen(...)`.
 *
 * Success — screen was loaded and displayed.
 * Error   — an error occurred while loading or showing the screen (e.g. timeout,
 * network issues, or missing screen configuration for the selected paywall).
 */
sealed class ApphudPaywallScreenShowResult {

    /** Screen was shown successfully. */
    object Success : ApphudPaywallScreenShowResult()

    /** Error occurred while loading or showing the screen. */
    data class Error(val error: ApphudError) : ApphudPaywallScreenShowResult()
}