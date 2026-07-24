package com.apphud.sdk.domain

data class Rule internal constructor(
    /**
     * For internal usage
     */
    internal val id: String,
    /**
     * For internal usage
     */
    internal val screenId: String,
    /**
     * Rule name from Apphud Dashboard.
     */
    val ruleName: String?,
    /**
     * Screen name from Apphud Dashboard.
     */
    val screenName: String?,
    /**
     * Your custom paywall identifier from Apphud Dashboard.
     */
    val paywallIdentifier: String? = null,
    /**
     * Internal Paywall ID associated with this rule, if any. Made public for Flutter/RN SDKs.
     */
    val paywallId: String? = null,
)
