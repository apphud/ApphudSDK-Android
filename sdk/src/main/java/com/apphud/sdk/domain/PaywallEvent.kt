package com.apphud.sdk.domain

import com.apphud.sdk.ApphudError

/**
 * Events emitted during paywall screen lifecycle.
 * Used for communication between FigmaWebViewActivity and ApphudInternal.
 */
sealed class PaywallEvent {

    /**
     * Emitted when user starts a transaction (purchase or restore).
     * @param product The product being purchased, or null for restore operations.
     */
    data class TransactionStarted(val product: ApphudProduct?) : PaywallEvent()

    /**
     * Emitted when a transaction is completed (successfully or with error).
     * @param result The result of the transaction.
     */
    data class TransactionCompleted(val result: ApphudPaywallScreenShowResult) : PaywallEvent()

    /**
     * Emitted when user taps the close button to dismiss the paywall.
     */
    object CloseButtonTapped : PaywallEvent()

    /**
     * Emitted when the paywall screen is successfully shown.
     */
    object ScreenShown : PaywallEvent()

    /**
     * Emitted when a screen error occurs (not related to transactions).
     * @param error The error that occurred.
     */
    data class ScreenError(val error: ApphudError) : PaywallEvent()
}