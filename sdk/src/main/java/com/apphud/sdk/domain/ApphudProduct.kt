package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetails.SubscriptionOfferDetails


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
    var productDetails: ProductDetails?,

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
    /**
     * @returns – Array of subscription offers with given Base Plan Id, or all offers.
     */
    fun subscriptionOffers(): List<SubscriptionOfferDetails>? {
        if (basePlanId != null) {
            return productDetails?.subscriptionOfferDetails?.filter { it.basePlanId == basePlanId }
        } else {
            return productDetails?.subscriptionOfferDetails
        }
    }
 }