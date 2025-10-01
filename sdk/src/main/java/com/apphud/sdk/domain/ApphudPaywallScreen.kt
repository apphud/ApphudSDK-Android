package com.apphud.sdk.domain

/**
 * Paywall screen configuration. Contains a Web-URL used to display the paywall
 * and a set of localized URLs.
 */
data class ApphudPaywallScreen(
    internal val id: String,
    /**
     * Default URL that will be opened.
     */
    val defaultUrl: String?,
    /**
     * Dictionary of localized URLs where key is a locale code ("en", "fr", etc.).
     */
    val urls: Map<String, String>,
)