package com.apphud.sdk.domain

data class ApphudPaywall(
    internal val id: String,
    /**
    Paywall name, from Apphud Dashboard.
     */
    val name: String,
    /**
    Your custom paywall identifier from Apphud Dashboard.
     */
    val identifier: String,
    /**
    It's possible to make a paywall default – it's a special alias name, that can be assigned to only ONE paywall at a time.
    There can be no default paywalls at all. It's up to you whether you want to have them or not.
     */
    val default: Boolean,
    /**
    Custom JSON from your paywall config.
    It could be titles, descriptions, localisations, font, background and color parameters, URLs to media content, etc.
     */
    val json: Map<String, Any>?,
    /**
    Array of products.
     */
    val products: List<ApphudProduct>?,
    /**
     * Screen configuration associated with the paywall.
     */
    val screen: ApphudPaywallScreen?,
    /**
    A/B test experiment name, if user is included in the experiment.
    You can use it for additional analytics.
     */
    val experimentName: String?,

    /**
    A/B Experiment Variation Name
     */
    val variationName: String?,

    /**
    Represents the identifier of a parent paywall from which an experiment variation was derived in A/B Experiments.
    This property is populated only if the 'Use existing paywall' option was selected
    during the setup of the experiment variation.
     */
    val parentPaywallIdentifier: String?,

    /**
    Current paywall's placement identifier, if available.
     */
    var placementIdentifier: String?,

    /**
     * For internal usage
     */
    internal var placementId: String?,
)
