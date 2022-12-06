package com.apphud.sdk

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.*


object Apphud {

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     */
    @kotlin.jvm.JvmStatic
    fun start(context: Context, apiKey: ApiKey) =
        start(context, apiKey, null)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     * @parameter userId: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    @kotlin.jvm.JvmStatic
    fun start(context: Context, apiKey: ApiKey, userId: UserId? = null) =
        start(context, apiKey, userId, null)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     * @parameter userId: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     * @parameter deviceID: Optional. You can provide your own unique device identifier. If null passed then UUID will be generated instead.
     */
    @kotlin.jvm.JvmStatic
    fun start(context: Context, apiKey: ApiKey, userId: UserId? = null, deviceId: DeviceId? = null)
    {
        ApphudUtils.setPackageName(context.packageName)
        ApphudInternal.initialize(context, apiKey, userId, deviceId)
    }

    /**
     * Updates user ID value. Note that it should be called only after user is registered, i.e.
     * inside ApphudListener's userDidRegister method.
     * - parameter userId: Required. New user ID value.
     */
    @kotlin.jvm.JvmStatic
    fun updateUserId(userId: UserId) = ApphudInternal.updateUserId(userId)

    /**
     * Returns current userID that identifies user across his multiple devices.
     */
    @kotlin.jvm.JvmStatic
    fun userId(): UserId = ApphudInternal.userId

    /**
     * Returns current device ID. You should use it only if you want to implement custom logout/login flow by saving User ID & Device ID pair for each app user.
     */
    fun deviceId(): String {
        return ApphudInternal.deviceId
    }

    /**
    Returns `true` if user has active subscription or non renewing purchase (lifetime).
    Note: You should not use this method if you have consumable in-app purchases, like coin packs.
    Use this method to determine whether or not user has active premium access.
    If you have consumable purchases, this method won't operate correctly,
    because Apphud SDK doesn't differ consumables from non-consumables.
     */
    @kotlin.jvm.JvmStatic
    fun hasPremiumAccess() : Boolean {
        return hasActiveSubscription() || nonRenewingPurchases().firstOrNull{ it.isActive() } != null
    }

    /**
     * Returns `true` if user has active subscription. Value is cached on device.
     * Use this method to determine whether or not user has active premium subscription.
     * Note that if you have lifetime purchases, you must use another `isNonRenewingPurchaseActive` method.
     */
    @kotlin.jvm.JvmStatic
    fun hasActiveSubscription(): Boolean = subscriptions().firstOrNull { it.isActive() } != null

    /**
     * Returns subscription object that current user has ever purchased. Subscriptions are cached on device.
     * Note: If returned object is not null, it doesn't mean that subscription is active.
     * You should check `ApphudSdk.hasActiveSubscription()` method or `subscription.isActive()`
     *      value to determine whether or not to unlock premium functionality to the user.
     */
    @kotlin.jvm.JvmStatic
    fun subscription(): ApphudSubscription? =
        ApphudInternal.currentUser?.subscriptions?.firstOrNull()

    /**
     * Returns an array of all subscriptions that this user has ever purchased. Subscriptions are cached on device.
     */
    @kotlin.jvm.JvmStatic
    fun subscriptions(): List<ApphudSubscription> =
        ApphudInternal.currentUser?.subscriptions ?: emptyList()

    /**
     * Returns an array of all in-app product purchases that this user has ever purchased.
     * Purchases are cached on device. This array is sorted by purchase date.
     */
    @kotlin.jvm.JvmStatic
    fun nonRenewingPurchases(): List<ApphudNonRenewingPurchase> =
        ApphudInternal.currentUser?.purchases?: emptyList()

    /**
     * Returns paywalls configured in Apphud Dashboard > Product Hub > Paywalls.
     * Each paywall contains an array of `ApphudProduct` objects that you use for purchase.
     * `ApphudProduct` is Apphud's wrapper around `SkuDetails`.
     * Returns empty array if paywalls are not yet fetched.
     * To get notified when paywalls are ready to use, use ApphudListener's  `userDidLoad` or `paywallsDidFullyLoad` methods,
     * depending on whether or not you need `SkuDetails` to be already filled in paywalls.
     * Best practice is to use this method together with `paywallsDidFullyLoad` listener.
     */
    fun paywalls() :List<ApphudPaywall> {
        return ApphudInternal.getPaywalls()
    }

    /**
     * Returns permission groups configured in Apphud dashboard > Product Hub > Products. Groups are cached on device.
     * Note that this method returns empty array if `SkuDetails` are not yet fetched from Google Play.
     * To get notified when `permissionGroups` are ready to use, use ApphudListener's
     * `apphudFetchSkuDetailsProducts` or `paywallsDidFullyLoad` methods or `productsFetchCallback`.
     * When any of these methods is called, `SkuDetails` are loaded, which means that current
     * `permissionGroups` method is ready to use.
     * Best practice is not to use this method at all, but use `paywalls()` instead.
     */
    fun permissionGroups(): List<ApphudGroup> {
        return ApphudInternal.permissionGroups()
    }

    /**
     * Returns `true` if current user has purchased in-app product with given product identifier.
     * Returns `false` if this product is refunded or never purchased.
     * Note: Purchases are sorted by purchase date, so it returns Bool value for the most recent purchase by given product identifier.
     */
    @kotlin.jvm.JvmStatic
    fun isNonRenewingPurchaseActive(productId: ProductId): Boolean =
        ApphudInternal.currentUser?.purchases
            ?.firstOrNull { it.productId == productId }?.isActive() ?: false

    /**
     * Submit attribution data to Apphud from your attribution network provider.
     * @data: Required. Attribution dictionary.
     * @provider: Required. Attribution provider name.
     * @identifier: Optional. Identifier that matches Apphud and Attribution provider.
     */
    @kotlin.jvm.JvmStatic
    fun addAttribution(
        provider: ApphudAttributionProvider,
        data: Map<String, Any>? = null,
        identifier: String? = null
    ) = ApphudInternal.addAttribution(provider, data, identifier)

    /**
     * You should use this method only in Observer mode.
     * This method will send all the purchases to the Apphud server.
     * If you use Apphud SDK as observer, you should call this method after every successful purchase or restoration.
     */
    @kotlin.jvm.JvmStatic
    fun syncPurchases(paywallIdentifier: String? = null) = ApphudInternal.syncPurchases(paywallIdentifier)

    /**
     * Implements `Restore Purchases` mechanism. Basically it just sends current Play Market Purchase Tokens to Apphud and returns subscriptions info.
     * Even if callback returns some subscription, it doesn't mean that subscription is active. You should check `subscription.isActive()` value.
     * @param callback: Required. Returns array of subscriptions, in-app products or optional, error.
     */
    @kotlin.jvm.JvmStatic
    fun restorePurchases(callback: ApphudPurchasesRestoreCallback) {
        ApphudInternal.restorePurchases(callback)
    }

    /**
     * Refreshes current purchases: subscriptions, promotionals or non-renewing purchases.
     * To get notified about updates, you should listen for ApphudListener's
     * apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) and
     * apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) methods.
     * You should not call this method on app launch, because Apphud SDK does it automatically.
     * Best practice is to refresh the user when a promotional has been granted on the web
     * or when your app reactivates from a background, if needed.
     */
    @kotlin.jvm.JvmStatic
    fun refreshEntitlements() {
        ApphudInternal.refreshEntitlements()
    }

    @kotlin.jvm.JvmStatic
    fun trackPurchase(purchases: List<Purchase>, paywallIdentifier: String? = null) = ApphudInternal.trackPurchase(purchases, paywallIdentifier)

    /**
     * Returns array of `SkuDetails` objects, identifiers of which you added in Apphud > Product Hub > Products.
     * Note that this method will return **null** if products are not yet fetched.
     * To get notified when `products` are ready to use, use ApphudListener's
     * `apphudFetchSkuDetailsProducts` or `paywallsDidFullyLoad` methods or `productsFetchCallback`.
     * When any of these methods is called, `SkuDetails` are loaded, which means that current
     * `products` method is ready to use.
     * Best practice is not to use this method at all, but use `paywalls()` instead.
     */
    @Deprecated("Use \"getPaywalls\" method instead.",
        ReplaceWith("getPaywalls(callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit)"))
    @kotlin.jvm.JvmStatic
    fun products(): List<SkuDetails>? {
        return ApphudInternal.getSkuDetailsList()
    }

    /**
     * This callback is called when `SkuDetails` are fetched from Google Play Billing.
     * Note that you have to add all product identifiers in Apphud > Product Hub > Products.
     * You can use `productsDidFetchCallback` callback
     * or implement `apphudFetchSkuDetailsProducts` listener method. Use whatever you like most.
     */
    @Deprecated("Use \"getPaywalls\" method instead.",
        ReplaceWith("getPaywalls(callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit)"))
    @kotlin.jvm.JvmStatic
    fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit) {
        ApphudInternal.productsFetchCallback(callback)
    }

    /**
     * Returns `SkuDetails` object by product identifier.
     * Note that you have to add this product identifier in Apphud > Product Hub > Products.
     * Will return `null` if product is not yet fetched from Google Play.
     */
    @Deprecated("Use \"getPaywalls\" method instead.",
        ReplaceWith("getPaywalls(callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit)"))
    @kotlin.jvm.JvmStatic
    fun product(productIdentifier: String): SkuDetails? {
        return ApphudInternal.getSkuDetailsByProductId(productIdentifier)
    }

    /**
     * Purchase product and automatically submit Google Play purchase token to Apphud
     *
     * @param activity current Activity for use
     * @param details The SkuDetails of the product you wish to purchase
     * @param block Optional. Returns `ApphudPurchaseResult` object.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, product: ApphudProduct, block: ((ApphudPurchaseResult) -> Unit)?) =
        ApphudInternal.purchase(activity, null, null, product, true, block)

    /**
     * Purchase product by id and automatically submit Google Play purchase token to Apphud

     * @param activity: current Activity for use
     * @param productId: The identifier of the product you wish to purchase
     * @param block: Optional. Returns `ApphudPurchaseResult` object.
     */
    @Deprecated("Purchase product by product identifier",
        ReplaceWith("purchase(activity: Activity, product: ApphudProduct, block: ((ApphudPurchaseResult) -> Unit)?)"))
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, productId: String, block: ((ApphudPurchaseResult) -> Unit)?) =
        ApphudInternal.purchase(activity, productId, null, null, true, block)

    /**
     * Purchase product and automatically submit Google Play purchase token to Apphud.
     *
     * @param activity current Activity for use
     * @param details The SkuDetails of the product you wish to purchase
     * @param block Optional. Returns `ApphudPurchaseResult` object.
     */
    @Deprecated("Purchase product by product identifier",
        ReplaceWith("purchase(activity: Activity, product: ApphudProduct, block: ((ApphudPurchaseResult) -> Unit)?)"))
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, details: SkuDetails, block: ((ApphudPurchaseResult) -> Unit)?) =
        ApphudInternal.purchase(activity, null, details, null, true, block)

    /**
     * Purchase product by id and automatically submit Google Play purchase token to Apphud.
     *
     * This method doesn't wait until Apphud validates purchase from Google Play and immediately returns result object.
     * This method may be useful if you don't care about purchases validation in callback.
     *
     * Note: When using this method properties `subscription` and `nonRenewingPurchase` in `ApphudPurchaseResult` will always be `null` !
     *
     * @param activity: current Activity for use
     * @param productId: The identifier of the product you wish to purchase
     * @param block: The closure that will be called when purchase completes.
     */
    @kotlin.jvm.JvmStatic
    fun purchaseWithoutValidation(activity: Activity, productId: String, block: ((ApphudPurchaseResult) -> Unit)?) =
        ApphudInternal.purchase(activity, productId, null,null,false, block)

    /**
     * Purchase sku product and automatically submit Google Play purchase token to Apphud.
     *
     * This method doesn't wait until Apphud validates purchase from Google Play and immediately returns result object.
     * This method may be useful if you don't care about purchases validation in callback.
     *
     * When using this method properties `subscription` and `nonRenewingPurchase` in `ApphudPurchaseResult` will always be `null` !
     *
     * @param activity current Activity for use
     * @param details The SkuDetails of the product you wish to purchase
     * @param block The closure that will be called when purchase completes.
     */
    @kotlin.jvm.JvmStatic
    fun purchaseWithoutValidation(activity: Activity, details: SkuDetails, block: ((ApphudPurchaseResult) -> Unit)?) =
        ApphudInternal.purchase(activity,null, details, null, false, block)

    /**
     * Set custom user property.
     * Value must be one of: "Int", "Float", "Double", "Boolean", "String" or "null".
     *
     * Example:
     * // use built-in property key
     * Apphud.setUserProperty(key: ApphudUserPropertyKey.Email, value: "user4@example.com", setOnce: true)
     * // use custom property key
     * Apphud.setUserProperty(key: ApphudUserPropertyKey.CustomProperty("custom_test_property_1"), value: 0.5)
     *
     * __Note__: You can use several built-in keys with their value types:
     * "ApphudUserPropertyKey.Email": User email. Value must be String.
     * "ApphudUserPropertyKey.Name": User name. Value must be String.
     * "ApphudUserPropertyKey.Phone": User phone number. Value must be String.
     * "ApphudUserPropertyKey.Age": User age. Value must be Int.
     * "ApphudUserPropertyKey.Gender": User gender. Value must be one of: "male", "female", "other".
     * "ApphudUserPropertyKey.Cohort": User install cohort. Value must be String.
     *
     * @param key Required. Initialize class with custom string or using built-in keys. See example above.
     * @param value  Required/Optional. Pass "null" to remove given property from Apphud.
     * @param setOnce  Optional. Pass "true" to make this property non-updatable.
     */
    @kotlin.jvm.JvmStatic
    fun setUserProperty(key: ApphudUserPropertyKey, value: Any?, setOnce: Boolean = false) {
        ApphudInternal.setUserProperty(key = key, value = value, setOnce = setOnce, increment = false)
    }

    /**
     * Increment custom user property.
     * Value must be one of: "Int", "Float", "Double".
     *
     * Example:
     * Apphud.incrementUserProperty(key: ApphudUserPropertyKey.CustomProperty("progress"), by: 0.5)
     *
     * @param key Required. Use your custom string key or some of built-in keys.
     * @param by Required/Optional. You can pass negative value to decrement.
     */
    @kotlin.jvm.JvmStatic
    fun incrementUserProperty(key: ApphudUserPropertyKey, by: Any) {
        ApphudInternal.setUserProperty(key = key, value = by, setOnce = false, increment = true)
    }

    /**
     * Optional. Use this method when your paywall screen is displayed to the user.
     * Might be useful for integrations.
     * Only implement this if you know why you need it.
     */
    @kotlin.jvm.JvmStatic
    fun paywallShown(paywall: ApphudPaywall) {
        ApphudInternal.paywallShown(paywall)
    }

    /**
     * Optional. Use this method when your paywall screen is dismissed without purchase.
     * Might be useful for integrations.
     * Only implement this if you know why you need it.
     */
    @kotlin.jvm.JvmStatic
    fun paywallClosed(paywall: ApphudPaywall) {
        ApphudInternal.paywallClosed(paywall)
    }

    /**
     * Enable debug logs. Better to call this method before SDK initialization.
     */
    @kotlin.jvm.JvmStatic
    fun enableDebugLogs() = ApphudUtils.enableDebugLogs()

    @kotlin.jvm.JvmStatic
    fun disableAdTracking() = ApphudUtils.disableAdTracking()

    /**
     * Use this method if you have your custom login system with own backend logic.
     */
    @kotlin.jvm.JvmStatic
    fun logout() = ApphudInternal.logout()

    /**
     * Set a listener
     * @param apphudListener Any ApphudDelegate conformable object.
     */
    @kotlin.jvm.JvmStatic
    fun setListener(apphudListener: ApphudListener) {
        ApphudInternal.apphudListener = apphudListener
    }

    /**
    You can grant free promotional subscription to user. Returns `true` in a callback if promotional was granted.

    __Note__: You should pass either `productId` (recommended) or `permissionGroup` OR both parameters `nil`. Sending both `productId` and `permissionGroup` parameters will result in `productId` being used.

    - parameter daysCount: Required. Number of days of free premium usage. For lifetime promotionals just pass extremely high value, like 10000.
    - parameter productId: Optional*. Recommended. Product Id of promotional subscription. See __Note__ message above for details.
    - parameter permissionGroup: Optional*. Permission Group of promotional subscription. Use this parameter in case you have multiple permission groups. See __Note__ message above for details.
    - parameter callback: Optional. Returns `true` if promotional subscription was granted.
     */
    @kotlin.jvm.JvmStatic
    fun grantPromotional(daysCount: Int, productId: String?, permissionGroup: ApphudGroup? = null, callback: ((Boolean) -> Unit)? = null) {
        ApphudInternal.grantPromotional(daysCount, productId, permissionGroup, callback)
    }
}