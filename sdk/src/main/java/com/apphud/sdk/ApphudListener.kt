package com.apphud.sdk

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudSubscription

interface ApphudListener {

    /**
     * Returns array of subscriptions that user ever purchased. Empty array means user never purchased a subscription.
     * If you have just one subscription group in your app, you will always receive just one subscription in an array.
     * This method is called when subscription is purchased or updated
     * (for example, status changed from `trial` to `expired` or `isAutorenewEnabled` changed to `false`).
     * SDK also checks for subscription updates when app becomes active.
     */
    fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) = Unit

    /**
     * Called when any of non renewing purchases changes. Called when purchase is made or has been refunded.
     */
    fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) = Unit

    /**
    Returns array of `SkuDetails` objects after they are fetched from Billing.
    Note that you have to add all product identifiers in Apphud.
     */
    fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>)

    /**
    Called when user identifier was changed
     */
    fun apphudDidChangeUserID(userId: String)

    /**
    Called when paywalls are loaded, however SkuDetails may still be nil at the moment
     */
    fun paywallsDidLoad(paywalls: List<ApphudPaywall>)

    /**
    Called when paywalls are fully loaded with their SkuDetails
    */
    fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>)


    /**
     1. распараллелить загрузку продуктов v2/products и post v1/customers
     2. добавить ретрай на запросы post v1/customers и v2/products
     3. добавить эти два листенера выше, первый вызовется тогда, когда загрузятся пейволы, т.е. когда зарегался кастомер
     4. второй листенер пусть вызывается тогда, когда доступны SkuDetails в пейволах
     5.
     */
}