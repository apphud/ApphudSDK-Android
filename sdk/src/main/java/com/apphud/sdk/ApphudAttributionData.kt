package com.apphud.sdk

/**
 * Data class representing attribution data for user acquisition tracking.
 */
data class ApphudAttributionData(
    /**
     * Raw attribution data received from MMPs, such as AppsFlyer or Branch.
     * Pass only `rawData` if no custom override logic is needed.
     */
    val rawData: Map<String, Any>,

    /**
     * Overridden ad network responsible for user acquisition (e.g., "Meta Ads", "Google Ads").
     * Leave `null` if no custom override logic is needed.
     */
    val adNetwork: String? = null,

    /**
     * Overridden channel that drove the user acquisition (e.g., "Instagram Feed", "Google UAC").
     * Leave `null` if no custom override logic is needed.
     */
    val channel: String? = null,

    /**
     * Overridden campaign name associated with the attribution data.
     * Leave `null` if no custom override logic is needed.
     */
    val campaign: String? = null,

    /**
     * Overridden ad set name within the campaign.
     * Leave `null` if no custom override logic is needed.
     */
    val adSet: String? = null,

    /**
     * Overridden specific ad creative used in the campaign.
     * Leave `null` if no custom override logic is needed.
     */
    val creative: String? = null,

    /**
     * Overridden keyword associated with the ad campaign (if applicable).
     * Leave `null` if no custom override logic is needed.
     */
    val keyword: String? = null,

    /**
     * Custom attribution parameter for additional tracking or mapping.
     * Use this to store extra attribution data if needed.
     */
    val custom1: String? = null,

    /**
     * Another custom attribution parameter for extended tracking or mapping.
     * Use this to store extra attribution data if needed.
     */
    val custom2: String? = null,
)