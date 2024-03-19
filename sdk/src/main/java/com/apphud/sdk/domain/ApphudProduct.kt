package com.apphud.sdk.domain

import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.SkuDetails

data class ApphudProduct (
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
    internal var skuDetails: SkuDetails?,
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
    internal var paywallId: String? = null
) :IApphudProduct {

    override fun type() :ApphudProductType? {
        skuDetails?.let {
            if (it?.type == BillingClient.SkuType.SUBS) {
                return ApphudProductType.SUBS
            } else {
                return ApphudProductType.SUBS
            }
        }
        return null
    }

    override fun productId(): String? {
        skuDetails?.let {
            return it.sku
        }
        return null
    }

    override fun title(): String? {
        skuDetails?.let {
            return it.title
        }
        return null
    }

    override fun description(): String? {
        skuDetails?.let {
            return it.description
        }
        return null
    }

    override fun priceCurrencyCode(): String? {
        skuDetails?.let {
            return it.priceCurrencyCode
        }
        return null
    }

    override fun priceAmountMicros(): String? {
        skuDetails?.let {
            return it.priceAmountMicros
        }
        return null
    }

    override fun oneTimePurchaseOfferDetails(): OneTimePurchaseOfferDetails? {
        skuDetails?.let {
            return OneTimePurchaseOfferDetails(
                priceAmountMicros = it.oneTimePurchaseOfferDetails.priceAmountMicros,
                formattedPrice = it.oneTimePurchaseOfferDetails.formattedPrice,
                priceCurrencyCode = it.oneTimePurchaseOfferDetails.priceCurrencyCode,
                offerIdToken = it.oneTimePurchaseOfferDetails.offerIdToken
                )
        }
        return null
    }

    override fun subscriptionOfferDetails(): List<SubscriptionOfferDetails>? {
        skuDetails?.let {
            var result :MutableList<SubscriptionOfferDetails> = mutableListOf()

            for (offerDetails in it.subscriptionOfferDetails){

                var phases :MutableList<PricingPhase> = mutableListOf()
                for (phase in offerDetails.pricingPhases.pricingPhaseList){
                    val item = PricingPhase(
                        billingPeriod = phase.billingPeriod,
                        priceCurrencyCode = phase.priceCurrencyCode,
                        formattedPrice = phase.formattedPrice,
                        priceAmountMicros = phase.priceAmountMicros,
                        recurrenceMode = RecurrenceMode.getRecurringMode(phase.recurrenceMode),
                        billingCycleCount = phase.billingCycleCount)
                    phases.add(item)
                }

                val item = SubscriptionOfferDetails(
                    pricingPhases = PricingPhases(phases),
                    basePlanId = offerDetails.basePlanId,
                    offerId = offerDetails.offerId,
                    offerToken = offerDetails.offerToken,
                    offerTags = offerDetails.offerTags
                    )
                result.add(item)
            }
            return result
        }
        return null
    }

    override fun toString(): String {
        return "ApphudProduct(id: ${id}, productId: ${productId}, name: ${name}, basePlanId: ${basePlanId}, productDetails: ${productId()}, placementIdentifier: ${placementIdentifier}, paywallIdenfitier: ${paywallIdentifier}, placementId: ${placementId}, paywallId: ${paywallId})"
    }

}
