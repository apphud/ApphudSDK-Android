package com.apphud.sdk.domain

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.managers.priceAmountMicros
import com.apphud.sdk.managers.priceCurrencyCode

enum class ApphudProductType {
    SUBS,
    INAPP
}

enum class RecurrenceMode(val mode: Int) {
    FINITE_RECURRING(2),
    INFINITE_RECURRING(1),
    NON_RECURRING(3),
    UNDEFINED(0);

    companion object {
        fun getRecurringMode(mode: Int): RecurrenceMode {
            val result = RecurrenceMode.values().firstOrNull { it.mode == mode }
            result?.let {
                return it
            }
            return UNDEFINED
        }
    }
}

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

    fun type(): ApphudProductType? {
        productDetails?.let {
            if (it.productType == BillingClient.ProductType.SUBS) {
                return ApphudProductType.SUBS
            } else {
                return ApphudProductType.INAPP
            }
        }
        return null
    }

    fun productId(): String? {
        productDetails?.let {
            return it.productId
        }
        return null
    }

    fun title(): String? {
        productDetails?.let {
            return it.title
        }
        return null
    }

    fun description(): String? {
        productDetails?.let {
            return it.description
        }
        return null
    }

    fun priceCurrencyCode(): String? {
        productDetails?.let {
            return it.priceCurrencyCode()
        }
        return null
    }

    fun priceAmountMicros(): String? {
        productDetails?.let {
            return it.priceAmountMicros().toString()
        }
        return null
    }

    fun oneTimePurchaseOfferDetails(): OneTimePurchaseOfferDetails? {
        productDetails?.let {
            return OneTimePurchaseOfferDetails(
                priceAmountMicros = it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0L,
                formattedPrice = it.oneTimePurchaseOfferDetails?.formattedPrice,
                priceCurrencyCode = it.oneTimePurchaseOfferDetails?.priceCurrencyCode,
                offerIdToken = null
            )
        }
        return null
    }

    fun subscriptionOfferDetails(): List<SubscriptionOfferDetails>? {
        productDetails?.let {
            var result: MutableList<SubscriptionOfferDetails> = mutableListOf()

            for (offerDetails in it.subscriptionOfferDetails ?: listOf()) {

                var phases: MutableList<PricingPhase> = mutableListOf()
                for (phase in offerDetails.pricingPhases.pricingPhaseList) {
                    val item = PricingPhase(
                        billingPeriod = phase.billingPeriod,
                        priceCurrencyCode = phase.priceCurrencyCode,
                        formattedPrice = phase.formattedPrice,
                        priceAmountMicros = phase.priceAmountMicros,
                        recurrenceMode = RecurrenceMode.getRecurringMode(phase.recurrenceMode),
                        billingCycleCount = phase.billingCycleCount
                    )
                    phases.add(item)
                }

                val item = SubscriptionOfferDetails(
                    pricingPhases = PricingPhases(phases.toList()),
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

    companion object {
        fun apphudProduct(id: String): ApphudProduct {
            return ApphudProduct(id, id, id, "play_store", null, null, null, null, null, null)
        }
    }

    override fun toString(): String {
        return "ApphudProduct(id: ${id}, productId: ${productId}, name: ${name}, basePlanId: ${basePlanId}, productDetails: ${productId()}, placementIdentifier: ${placementIdentifier}, paywallIdenfitier: ${paywallIdentifier}, placementId: ${placementId}, paywallId: ${paywallId})"
    }
}
