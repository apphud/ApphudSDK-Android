package com.apphud.sdk.domain

import com.google.gson.annotations.SerializedName

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
    @SerializedName("urls")
    private val _urls: Map<String, String>? = null,
    /**
     * Screen name as set in the Apphud Dashboard.
     *
     * Falls back to `null` for legacy cached data saved before this field existed.
     */
    val name: String? = null,
) {
    /**
     * Dictionary of localized URLs where key is a locale code ("en", "fr", etc.).
     *
     * Falls back to an empty map for legacy cached data saved before this field existed.
     */
    val urls: Map<String, String> get() = _urls ?: emptyMap()
}