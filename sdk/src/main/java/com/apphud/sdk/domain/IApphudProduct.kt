package com.apphud.sdk.domain

enum class ApphudProductType(){
    SUBS(),
    INAPP
}

enum class RecurrenceMode (val mode: Int){
    FINITE_RECURRING(2),
    INFINITE_RECURRING(1),
    NON_RECURRING (3),
    UNDEFINED (0);

    companion object {
        fun getRecurringMode(mode: Int): RecurrenceMode {
            val result = RecurrenceMode.values().firstOrNull { it.mode == mode }
            result?.let {
                return it
            }
            return RecurrenceMode.UNDEFINED
        }
    }
}

interface IApphudProduct {
    fun type() :ApphudProductType?
    fun productId() :String?
    fun title() :String?
    fun description() :String?
    fun priceCurrencyCode(): String?
    fun priceAmountMicros(): String?

    fun oneTimePurchaseOfferDetails() :OneTimePurchaseOfferDetails?
    fun subscriptionOfferDetails() :List<SubscriptionOfferDetails>?

}

data class OneTimePurchaseOfferDetails (
    var priceAmountMicros: Long,
    var formattedPrice: String?,
    var priceCurrencyCode: String?,
    var offerIdToken: String?,
)


data class SubscriptionOfferDetails (
    var pricingPhases: PricingPhases?,
    var basePlanId: String?,
    var offerId: String?,
    var offerToken: String?,
    var offerTags: List<String>?
)

data class PricingPhases (
    var pricingPhaseList: List<PricingPhase>? = null
)


data class PricingPhase (
    var billingPeriod: String?,
    var priceCurrencyCode: String?,
    var formattedPrice: String?,
    var priceAmountMicros: Long,
    var recurrenceMode :RecurrenceMode,
    var billingCycleCount: Int
)
