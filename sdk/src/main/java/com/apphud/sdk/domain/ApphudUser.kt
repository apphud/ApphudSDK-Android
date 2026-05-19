package com.apphud.sdk.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.UserId
import org.json.JSONException
import org.json.JSONObject

data class ApphudUser(

    /**
     * Unique user identifier. This can be updated later.
     */
    val userId: UserId,

    /**
     * Currency Code based on user's locale or purchases.
     */
    val currencyCode: String?,

    /**
     * Country Code based on user's locale or purchases.
     */
    val countryCode: String?,

    /** Returns:
     * List<ApphudSubscription>: A list of user's subscriptions of any statuses.
     */
    var subscriptions: List<ApphudSubscription>,

    /** Returns:
     * List<ApphudNonRenewingPurchase>: A list of user's non-consumable or
     * consumable purchases, if any.
     */
    var purchases: List<ApphudNonRenewingPurchase>,

    /**
     * Number of devices associated with the same `userId`.
     *
     * You can use this value to detect suspicious account sharing and decide whether to limit premium access.
     * Falls back to `0` if the backend value is unavailable.
     */
    val totalDevicesCount: Int = 0,

    /**
     * Internal database id of the user. Should not be used in analytics.
     */
    val internalId: String = "",

    /**
     * Name of the active A/B test experiment assigned to this user.
     *
     * `null` when no experiment is assigned.
     */
    val experimentName: String? = null,

    /**
     * Name of the active variation assigned to this user.
     *
     * `null` when no variation is assigned.
     */
    val variationName: String? = null,

    /**
     * Name of the targeting (audience) the user matches into.
     *
     * `null` when no targeting is assigned.
     */
    val targetingName: String? = null,

    /**
     * The raw JSON string for the app-level remote configuration assigned to this user.
     *
     * This value is the unmodified payload received from the backend. Use it when you need
     * the exact server response, or call `remoteConfig()` to get a parsed `Map<String, Any>` representation.
     */
    val remoteConfigString: String? = null,

    /**
     * There properties are for internal usage, to get placements
     * use rawPlacements() function below
     */
    internal val placements: List<ApphudPlacement>,
    internal val isTemporary: Boolean?,
) {
    /**
     * Returns true if user has any subscriptions or non-renewing purchases.
     */
    fun hasPurchases(): Boolean {
        return subscriptions.isNotEmpty() || purchases.isNotEmpty()
    }

    /**
     * A list of paywall placements, potentially altered based on the user's
     * involvement in A/B testing, if any. A placement is a specific location
     * within a user's journey (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * __Important__: This function may return placement objects that do not yet
     * have ProductDetails attached.
     */
    fun rawPlacements(): List<ApphudPlacement> {
        return placements
    }

    /**
     * Global app-level remote configuration payload for the active user variation.
     *
     * The value is parsed from backend JSON and returned as a map.
     * Returns an empty map when config is missing or invalid.
     */
    fun remoteConfig(): Map<String, Any> {
        val raw = remoteConfigString ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            jsonObjectToMap(json)
        } catch (e: JSONException) {
            ApphudLog.logE("Failed to decode Remote Config for json string: $raw")
            emptyMap()
        }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            result[key] = unwrapJsonValue(value)
        }
        return result
    }

    private fun unwrapJsonValue(value: Any): Any {
        return when (value) {
            is JSONObject -> jsonObjectToMap(value)
            is org.json.JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    list.add(unwrapJsonValue(value.get(i)))
                }
                list
            }
            else -> value
        }
    }
}
