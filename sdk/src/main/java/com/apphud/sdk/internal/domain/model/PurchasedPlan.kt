package com.apphud.sdk.internal.domain.model

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudProduct

/**
 * Which of the products sharing one Google Play product id a purchase paywall event is about.
 * A part that isn't known is `null` and left out of the event.
 */
internal data class PurchasedPlan(val basePlanId: String?, val productBundleId: String?) {

    companion object {
        /** [details] and [offerToken]: what the checkout passes to Google Play. */
        fun of(product: ApphudProduct, details: ProductDetails, offerToken: String?): PurchasedPlan {
            val isSubscription = details.productType == BillingClient.ProductType.SUBS
            val productBasePlanId = product.basePlanId?.takeIf { isSubscription && it.isNotBlank() }
            val basePlanId = if (isSubscription) {
                details.subscriptionOfferDetails?.firstOrNull { it.offerToken == offerToken }?.basePlanId ?: productBasePlanId
            } else {
                null
            }
            // A product made up from a bare product id has that id for its own: it names no bundle. Neither
            // does a product of another base plan than the one bought, and a wrong bundle is worse than none.
            val bundleId = product.id?.takeIf {
                it.isNotBlank() && it != product.productId &&
                    (productBasePlanId == null || productBasePlanId == basePlanId)
            }
            return PurchasedPlan(basePlanId, bundleId)
        }
    }
}
