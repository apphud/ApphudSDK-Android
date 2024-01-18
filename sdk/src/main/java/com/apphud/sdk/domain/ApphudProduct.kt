package com.apphud.sdk.domain

import com.xiaomi.billingclient.api.SkuDetails

data class ApphudProduct(
    /**
     * Product id
     * */
    internal var id: String?,
    /**
     Product Identifier from Google Play.
     */
    var productId: String,
    /**
     Product name from Apphud Dashboard
     */
    var name: String?,
    /**
     Always `play_store` in Android SDK.
     */
    var store: String,
    /**
     * Base Plan Id of the product from Google Play Console
     */
    var basePlanId: String?,
    /**
     When paywalls are successfully loaded, productDetails model will always be present if Google Play returned model for this product id.
     getPaywalls method will return callback only when Google Play products are fetched and mapped with Apphud products.
     May be `null` if product identifier is invalid, or product is not available in Google Play.
     */
    var skuDetails: SkuDetails?,
    /**
     * Placement Identifier, if any.
     */
    var placementIdentifier: String?,
    /**
     User Generated Paywall Identifier
     */
    var paywallIdentifier: String?,
    /**
     * For internal usage
     * */
    internal var placementId: String?,
    /**
     * For internal usage
     */
    internal var paywallId: String?,
) {

    override fun toString(): String {
        return "ApphudProduct(id: ${id}, productId: ${productId}, name: ${name}, basePlanId: ${basePlanId}, productDetails: ${skuDetails?.sku ?: "N/A"}, placementIdentifier: ${placementIdentifier}, paywallIdenfitier: ${paywallIdentifier}, placementId: ${placementId}, paywallId: ${paywallId})"
    }
}
