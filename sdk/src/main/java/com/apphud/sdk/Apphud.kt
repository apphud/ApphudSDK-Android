package com.apphud.sdk

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription


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
        ApphudInternal.apiKey = apiKey
        ApphudInternal.context = context
        ApphudInternal.initialize(userId, deviceId)
    }

    /**
     * Updates user ID value
     *
     * - parameter userId: Required. New user ID value.
     */
    @kotlin.jvm.JvmStatic
    fun updateUserId(userId: UserId) = ApphudInternal.updateUserId(userId)

    /**
     * Returns current userID that identifies user across his multiple devices.
     */
    @kotlin.jvm.JvmStatic
    fun userId(): UserId? = ApphudInternal.userId

    /**
     * Returns true if user has active subscription.
     * Use this method to determine whether or not to unlock premium functionality to the user.
     */
    @kotlin.jvm.JvmStatic
    fun hasActiveSubscription(): Boolean = subscription()
        ?.isActive() ?: false

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
     * Use this method if you have more than one subsription group in your app.
     */
    @kotlin.jvm.JvmStatic
    fun subscriptions(): List<ApphudSubscription> =
        ApphudInternal.currentUser?.subscriptions ?: emptyList()

    /**
     * Returns an array of all standard in-app purchases (consumables, nonconsumables or nonrenewing subscriptions)
     * that this user has ever purchased. Purchases are cached on device. This array is sorted by purchase date.
     * Apphud only tracks consumables if they were purchased after integrating Apphud SDK.
     */
    @kotlin.jvm.JvmStatic
    fun nonRenewingPurchases(): List<ApphudNonRenewingPurchase> =
        ApphudInternal.currentUser?.purchases ?: emptyList()

    /**
     * Returns `true` if current user has purchased standard in-app purchase with given product identifier.
     * Returns `false` if this product is refunded or never purchased.
     * Includes consumables, nonconsumables or non-renewing subscriptions.
     * Apphud only tracks consumables if they were purchased after integrating Apphud SDK.
     *
     * Note: Purchases are sorted by purchase date, so it returns Bool value for the most recent purchase by given product identifier.
     */
    @kotlin.jvm.JvmStatic
    fun isNonRenewingPurchaseActive(productId: ProductId): Boolean =
        ApphudInternal.currentUser?.purchases
            ?.firstOrNull { it.productId == productId }?.isActive() ?: false

    /**
     * Submit attribution data to Apphud from your attribution network provider.
     * @data: Required. Attribution dictionary.
     * @provider: Required. Attribution provider name. Available values: .appsFlyer. Will be added more soon.
     * @identifier: Optional. Identifier that matches Apphud and Attrubution provider. Required for AppsFlyer.
     */
    @kotlin.jvm.JvmStatic
    fun addAttribution(
        provider: ApphudAttributionProvider,
        data: Map<String, Any>? = null,
        identifier: String? = null
    ) = ApphudInternal.addAttribution(provider, data, identifier)

    /**
     * This method will send all the purchases to the Apphud server.
     * Call this when using your own implementation for subscriptions anytime a sync is needed, like after a successful purchase.
     */
    @kotlin.jvm.JvmStatic
    fun syncPurchases() = ApphudInternal.syncPurchases()

    /**
     * Returns an array of **SkuDetails** objects that you added in Apphud.
     * Note that this method will return **null** if products are not yet fetched.
     */
    @kotlin.jvm.JvmStatic
    fun products(): List<SkuDetails>? {
        return ApphudInternal.getSkuDetailsList()
    }

    /**
     * This callback is called when SKProducts are fetched from Google Play Billing.
     * Note that you have to add all product identifiers in Apphud.
     * You can use `productsDidFetchCallback` callback
     * or implement `apphudFetchSkuDetailsProducts` listener method. Use whatever you like most.
     */

    @kotlin.jvm.JvmStatic
    fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit) {
        ApphudInternal.productsFetchCallback(callback)
    }

    /**
     * Returns **SkuDetails** object by product identifier.
     * Note that you have to add this product identifier in Apphud.
     * Will return **null** if product is not yet fetched from Google Play Billing.
     */
    @kotlin.jvm.JvmStatic
    fun product(productIdentifier: String): SkuDetails? {
        return ApphudInternal.getSkuDetailsByProductId(productIdentifier)
    }

    /**
     * Purchases product and automatically submit
     * @param activity: current Activity for use
     * @param productId: The identifier of the product you wish to purchase
     * @param onComplete: The closure that will be called when purchase completes.
     * @param onCancel: The closure that will be called when purchase was cancelled or productId is invalid.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, productId: String, onComplete: (List<Purchase>) -> Unit, onCancel: () -> Unit = {}) =
        ApphudInternal.purchase(activity, productId, onComplete, onCancel)

    /**
     * Purchases product and automatically submit
     * @param activity current Activity for use
     * @param details The skuDetails of the product you wish to purchase
     * @param onComplete The closure that will be called when purchase completes.
     * @param onCancel: The closure that will be called when purchase was cancelled or details was invalid.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, details: SkuDetails, onComplete: (List<Purchase>) -> Unit, onCancel: () -> Unit = {}) =
        ApphudInternal.purchase(activity, details, onComplete, onCancel)


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
     * Enables debug logs. Better to call this method before SDK initialization.
     */
    @kotlin.jvm.JvmStatic
    fun enableDebugLogs() = ApphudUtils.enableDebugLogs()

    @kotlin.jvm.JvmStatic
    fun disableAdTracking() = ApphudUtils.disableAdTracking()

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
}