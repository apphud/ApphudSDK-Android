package com.apphud.sdk

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Apphud {
    //region === Initialization ===

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     */
    @kotlin.jvm.JvmStatic
    fun start(
        context: Context,
        apiKey: ApiKey,
        callback: ((ApphudUser) -> Unit)? = null,
    ) = start(context, apiKey, null, null, callback)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     * @parameter userId: Optional. You can provide your own unique user identifier.
     * If null passed then UUID will be generated instead.
     */
    @kotlin.jvm.JvmStatic
    fun start(
        context: Context,
        apiKey: ApiKey,
        userId: UserId? = null,
        callback: ((ApphudUser) -> Unit)? = null,
    ) = start(context, apiKey, userId, null, callback)

    /**
     * Not recommended. Use this type of initialization with care.
     * Passing different Device ID will create a new user in Apphud.
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     * @parameter userId: Optional. You can provide your own unique user identifier.
     * If null passed then UUID will be generated instead.
     * @parameter deviceID: Optional. You can provide your own unique device identifier.
     * If null passed then UUID will be generated instead.
     */
    @kotlin.jvm.JvmStatic
    fun start(
        context: Context,
        apiKey: ApiKey,
        userId: UserId? = null,
        deviceId: DeviceId? = null,
        callback: ((ApphudUser) -> Unit)? = null,
    ) {
        ApphudUtils.setPackageName(context.packageName)
        ApphudInternal.initialize(context, apiKey, userId, deviceId, callback)
    }

    /**
     * Set a listener
     * @param apphudListener Any ApphudDelegate conformable object.
     */
    @kotlin.jvm.JvmStatic
    fun setListener(apphudListener: ApphudListener) {
        ApphudInternal.apphudListener = apphudListener
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
    @kotlin.jvm.JvmStatic
    fun deviceId(): String {
        return ApphudInternal.deviceId
    }

    //endregion
    //region === Placements, Paywalls and Products ===

    /**
     * Returns placements configured in Apphud Dashboard > Product Hub > Placements.
     * Each paywall contains an array of `ApphudProduct` objects that you use for purchase.
     * This callback is called when paywalls are populated with their `ProductDetails` objects.
     * Callback is called immediately if paywalls are already loaded.
     * To get notified when paywalls are loaded without `ProductDetails`,
     * use `userDidLoad()` method of ApphudListener.
     */
    @kotlin.jvm.JvmStatic
    fun placementsDidLoadCallback(callback: (List<ApphudPlacement>?) -> Unit) {
        ApphudInternal.performWhenOfferingsPrepared { callback(ApphudInternal.placements) }
    }

    /**
     * Returns `ApphudPlacement` configured in Apphud Dashboard > Product Hub > Placements.
     * Each Placement contains `ApphudPaywall` object that you use for purchase.
     * This method suspends until inner ProductDetails are loaded from Google Play.
     * Method returns immediately if placements are already loaded.
     */
    @kotlin.jvm.JvmStatic
    suspend fun placements(): List<ApphudPlacement>? =
        suspendCancellableCoroutine { continuation ->
            ApphudInternal.performWhenOfferingsPrepared {
                if (!continuation.isCompleted) {
                    continuation.resume(ApphudInternal.placements)
                }
            }
        }

    /**
     * Returns paywalls configured in Apphud Dashboard > Product Hub > Paywalls.
     * Each paywall contains an array of `ApphudProduct` objects that you use for purchase.
     * This callback is called when paywalls are populated with their `ProductDetails` objects.
     * Callback is called immediately if paywalls are already loaded.
     * To get notified when paywalls are loaded without `ProductDetails`,
     * use `userDidLoad()` method of ApphudListener.
     */
    @kotlin.jvm.JvmStatic
    fun paywallsDidLoadCallback(callback: (List<ApphudPaywall>) -> Unit) {
        ApphudInternal.performWhenOfferingsPrepared { callback(ApphudInternal.getPaywalls()) }
    }

    /**
     * Returns paywalls configured in Apphud Dashboard > Product Hub > Paywalls.
     * Each paywall contains an array of `ApphudProduct` objects that you use for purchase.
     * `ApphudProduct` is Apphud's wrapper around `ProductsDetails`.
     * This method suspends until inner ProductDetails are loaded from Google Play.
     * Returns empty array if paywalls are not yet fetched.
     * To get notified when paywalls are ready to use, use ApphudListener's  `userDidLoad` or `paywallsDidFullyLoad` methods,
     * depending on whether or not you need `ProductsDetails` to be already filled in paywalls.
     * Best practice is to use this method together with `paywallsDidFullyLoad` listener.
     */
    @kotlin.jvm.JvmStatic
    suspend fun paywalls(): List<ApphudPaywall> =
        suspendCancellableCoroutine { continuation ->
            ApphudInternal.performWhenOfferingsPrepared {
                if (!continuation.isCompleted) {
                    continuation.resume(ApphudInternal.getPaywalls())
                }
            }
        }

    /**
     * Use this method when your paywall screen is displayed to the user.
     * Required for A/B testing analysis.
     */
    @kotlin.jvm.JvmStatic
    fun paywallShown(paywall: ApphudPaywall) {
        ApphudInternal.paywallShown(paywall)
    }

    /**
     * Use this method when your paywall screen is dismissed without a purchase.
     * Required for A/B testing analysis.
     */
    @kotlin.jvm.JvmStatic
    fun paywallClosed(paywall: ApphudPaywall) {
        ApphudInternal.paywallClosed(paywall)
    }

    /**
     * Returns permission groups configured in Apphud dashboard > Product Hub > Products. Groups are cached on device.
     * Note that this method returns empty array if `ProductsDetails` are not yet fetched from Google Play.
     * To get notified when `permissionGroups` are ready to use, use ApphudListener's
     * `apphudFetchProductsDetailsProducts` or `paywallsDidFullyLoad` methods or `productsFetchCallback`.
     * When any of these methods is called, `ProductsDetails` are loaded, which means that current
     * `permissionGroups` method is ready to use.
     * Best practice is not to use this method at all, but use `paywalls()` instead.
     */
    @kotlin.jvm.JvmStatic
    fun permissionGroups(): List<ApphudGroup> {
        return ApphudInternal.permissionGroups()
    }

    /**
     * Returns array of `ProductsDetails` objects, identifiers of which you added in Apphud > Product Hub > Products.
     * Note that this method will return **null** if products are not yet fetched.
     * To get notified when `products` are ready to use, use ApphudListener's
     * `apphudFetchProductsDetails` or `paywallsDidFullyLoad` methods or `productsFetchCallback`.
     * When any of these methods is called, `ProductsDetails` are loaded, which means that current
     * `products` method is ready to use.
     * Best practice is not to use this method at all, but use `paywalls()` instead.
     */
    @Deprecated(
        "Use \"getPaywalls\" method instead.",
        ReplaceWith("getPaywalls(callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit)"),
    )
    @kotlin.jvm.JvmStatic
    fun products(): List<ProductDetails>? {
        return ApphudInternal.getProductDetailsList()
    }

    /**
     * This callback is called when `ProductsDetails` are fetched from Google Play Billing.
     * Note that you have to add all product identifiers in Apphud > Product Hub > Products.
     * You can use `productsDidFetchCallback` callback
     * or implement `apphudFetchProductsDetails` listener method. Use whatever you like most.
     */
    @Deprecated(
        "Use \"getPaywalls\" method instead.",
        ReplaceWith("getPaywalls(callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit)"),
    )
    @kotlin.jvm.JvmStatic
    fun productsFetchCallback(callback: (List<ProductDetails>) -> Unit) {
        ApphudInternal.productsFetchCallback(callback)
    }

    /**
     * Returns `ProductsDetails` object by product identifier.
     * Note that you have to add this product identifier in Apphud > Product Hub > Products.
     * Will return `null` if product is not yet fetched from Google Play.
     */
    @Deprecated(
        "Use \"getPaywalls\" method instead.",
        ReplaceWith("getPaywalls(callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit)"),
    )
    @kotlin.jvm.JvmStatic
    fun product(productIdentifier: String): ProductDetails? {
        return ApphudInternal.getProductDetailsByProductId(productIdentifier)
    }

    //endregion
    //region === Purchases ===

    /**
     Returns `true` if user has active subscription or non renewing purchase (lifetime).
     Note: You should not use this method if you have consumable in-app purchases, like coin packs.
     Use this method to determine whether or not user has active premium access.
     If you have consumable purchases, this method won't operate correctly,
     because Apphud SDK doesn't differ consumables from non-consumables.
     */
    @kotlin.jvm.JvmStatic
    fun hasPremiumAccess(): Boolean {
        return hasActiveSubscription() || nonRenewingPurchases().firstOrNull { it.isActive() } != null
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
    fun subscription(): ApphudSubscription? = ApphudInternal.subscriptions().firstOrNull()

    /**
     * Returns an array of all subscriptions that this user has ever purchased. Subscriptions are cached on device.
     */
    @kotlin.jvm.JvmStatic
    fun subscriptions(): List<ApphudSubscription> = ApphudInternal.subscriptions()

    /**
     * Returns an array of all in-app product purchases that this user has ever purchased.
     * Purchases are cached on device. This array is sorted by purchase date.
     */
    @kotlin.jvm.JvmStatic
    fun nonRenewingPurchases(): List<ApphudNonRenewingPurchase> = ApphudInternal.purchases()

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
     * Purchase product and automatically submit Google Play purchase token to Apphud
     *
     * @param activity Required. Current Activity for use
     * @param product Required. ApphudProduct to purchase
     * @param offerIdToken Optional. Specifies the identifier of the offer to initiate purchase with. You must manually select base plan
     * and offer from ProductDetails and pass offer id token.
     * @param oldToken Optional.Specifies the Google Play Billing purchase token that the user is upgrading or downgrading from.
     * @param replacementMode Optional.Replacement mode (https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.ReplacementMode?hl=en)
     * @param consumableInappProduct Optional. Default false. Pass true for consumables products.
     * @param block Optional. Returns `ApphudPurchaseResult` object.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(
        activity: Activity,
        apphudProduct: ApphudProduct,
        offerIdToken: String? = null,
        oldToken: String? = null,
        replacementMode: Int? = null,
        consumableInappProduct: Boolean = false,
        block: ((ApphudPurchaseResult) -> Unit)?,
    ) = ApphudInternal.purchase(
        activity = activity,
        apphudProduct = apphudProduct,
        productId = null,
        offerIdToken = offerIdToken,
        oldToken = oldToken,
        replacementMode = replacementMode,
        consumableInappProduct = consumableInappProduct,
        callback = block,
    )

    /**
     * Purchase product and automatically submit Google Play purchase token to Apphud
     *
     * @param activity Required. Current Activity for use
     * @param productId Required. Google Play product id
     * @param offerIdToken Optional. Specifies the identifier of the offer to initiate purchase with. You must manually select base plan
     * and offer from ProductDetails and pass offer id token.
     * @param oldToken Optional.Specifies the Google Play Billing purchase token that the user is upgrading or downgrading from.
     * @param replacementMode Optional.Replacement mode (https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.SubscriptionUpdateParams.ReplacementMode?hl=en)
     * @param consumableInappProduct Optional. Default false. Pass true for consumables products.
     * @param block Optional. Returns `ApphudPurchaseResult` object.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(
        activity: Activity,
        productId: String,
        offerIdToken: String? = null,
        oldToken: String? = null,
        replacementMode: Int? = null,
        consumableInappProduct: Boolean = false,
        block: ((ApphudPurchaseResult) -> Unit)?,
    ) = ApphudInternal.purchase(
        activity = activity,
        apphudProduct = null,
        productId = productId,
        offerIdToken = offerIdToken,
        oldToken = oldToken,
        replacementMode = replacementMode,
        consumableInappProduct = consumableInappProduct,
        callback = block,
    )

    /**
     * __Only in Observer Mode__: call this method after every successful purchase.
     *
     * __Passing offerIdToken is mandatory for subscriptions!__
     * This method submits successful purchase to Apphud.
     * Pass `paywallIdentifier` and `placementIdentifier` to be able to use A/B tests in Observer Mode. See https://docs.apphud.com/docs/observer-mode#android for details.
     */
    @kotlin.jvm.JvmStatic
    fun trackPurchase(
        purchase: Purchase,
        productDetails: ProductDetails,
        offerIdToken: String?,
        paywallIdentifier: String? = null,
        placementIdentifier: String? = null,
    ) = ApphudInternal.trackPurchase(purchase, productDetails, offerIdToken, paywallIdentifier, placementIdentifier)

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

    //endregion
    //region === Attribution ===

    /**
     * Collects device identifiers that are required for some third-party integrations, like AppsFlyer, Adjust, Singular, etc.
     * Identifiers include Advertising ID, Android ID, App Set ID.
     * @warning When targeting Android 13 and above, you must declare AD_ID permission in the manifest file: https://support.google.com/googleplay/android-developer/answer/6048248?hl=en
     * @warning Be sure optOutOfTracking() not called before. Otherwise device identifiers will not be collected.
     */
    @kotlin.jvm.JvmStatic
    fun collectDeviceIdentifiers() {
        ApphudInternal.collectDeviceIdentifiers()
    }

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
        identifier: String? = null,
    ) = ApphudInternal.addAttribution(provider, data, identifier)

    //endregion
    //region === User Properties ===

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
    fun setUserProperty(
        key: ApphudUserPropertyKey,
        value: Any?,
        setOnce: Boolean = false,
    ) {
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
    fun incrementUserProperty(
        key: ApphudUserPropertyKey,
        by: Any,
    ) {
        ApphudInternal.setUserProperty(key = key, value = by, setOnce = false, increment = true)
    }

    //endregion
    //region === Other ===

    /**
     You can grant free promotional subscription to user. Returns `true` in a callback if promotional was granted.

     __Note__: You should pass either `productId` (recommended) or `permissionGroup` OR both parameters `nil`. Sending both `productId` and `permissionGroup` parameters will result in `productId` being used.

     - parameter daysCount: Required. Number of days of free premium usage. For lifetime promotionals just pass extremely high value, like 10000.
     - parameter productId: Optional*. Recommended. Product Id of promotional subscription. See __Note__ message above for details.
     - parameter permissionGroup: Optional*. Permission Group of promotional subscription. Use this parameter in case you have multiple permission groups. See __Note__ message above for details.
     - parameter callback: Optional. Returns `true` if promotional subscription was granted.
     */
    @kotlin.jvm.JvmStatic
    fun grantPromotional(
        daysCount: Int,
        productId: String?,
        permissionGroup: ApphudGroup? = null,
        callback: ((Boolean) -> Unit)? = null,
    ) {
        ApphudInternal.grantPromotional(daysCount, productId, permissionGroup, callback)
    }

    /**
     * Enable debug logs. Better to call this method before SDK initialization.
     */
    @kotlin.jvm.JvmStatic
    fun enableDebugLogs() = ApphudUtils.enableDebugLogs()

    /**
     * Use this method if you have your custom login system with own backend logic.
     */
    @kotlin.jvm.JvmStatic
    fun logout() = ApphudInternal.logout()

    /**
     * Must be called before SDK initialization. If called, some user parameters like Advertising ID, Android ID, App Set ID, Device Type, IP address will not be tracked by Apphud.
     */
    @kotlin.jvm.JvmStatic
    fun optOutOfTracking() {
        ApphudUtils.optOutOfTracking = true
    }
    //endregion
}
