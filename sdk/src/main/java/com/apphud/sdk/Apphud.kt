package com.apphud.sdk

import android.app.Activity
import android.content.Context
import com.apphud.sdk.domain.*
import com.xiaomi.billingclient.api.Purchase
import com.xiaomi.billingclient.api.SkuDetails
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Apphud {
    //region === Initialization ===

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @param context The application context.
     * @param apiKey Your API key. This is a required parameter.
     * @param callback (Optional) A callback function that is invoked with the `ApphudUser`
     *                 object after the SDK initialization is complete. __Note__: Do not store
     *                 `ApphudUser`
     *                 instance in your own code, since it may change at runtime.
     */
    fun start(
        activity: Activity,
        apiKey: ApiKey,
        callback: ((ApphudUser) -> Unit)? = null,
    ) = start(activity, apiKey, null, null, callback)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @param context The application context.
     * @param apiKey Your API key. This is a required parameter.
     * @param userId (Optional) A unique user identifier. If null is passed, a UUID will be
     *               generated and used as the user identifier.
     * @param callback (Optional) A callback function that is invoked with the `ApphudUser`
     *                 object after the SDK initialization is complete. __Note__: Do not store
     *                 `ApphudUser`
     *                 instance in your own code, since it may change at runtime.
     */
    fun start(
        activity: Activity,
        apiKey: ApiKey,
        userId: UserId? = null,
        callback: ((ApphudUser) -> Unit)? = null,
    ) = start(activity, apiKey, userId, null, callback)

    /**
     * Initializes the Apphud SDK. This method should be called during the app launch.
     *
     * @param context The application context.
     * @param apiKey Your API key. This is a required parameter.
     * @param userId (Optional) A unique user identifier. If null is passed, a UUID will be
     *               generated and used as the user identifier.
     * @param deviceId (Optional) A unique device identifier. If null is passed, a UUID will be
     *                 generated and used as the device identifier. __Important__: Use this
     *                 parameter with caution. Passing different device IDs
     *                 can result in the creation of multiple user records in Apphud for the same
     *                 actual user. Best practice is to always pass null.
     * @param callback (Optional) A callback function that is invoked with the `ApphudUser`
     *                 object after the SDK initialization is complete. __Note__: Do not store
     *                 `ApphudUser`
     *                 instance in your own code, since it may change at runtime.
     */
    fun start(
        activity: Activity,
        apiKey: ApiKey,
        userId: UserId? = null,
        deviceId: DeviceId? = null,
        callback: ((ApphudUser) -> Unit)? = null,
    ) {
        ApphudUtils.setPackageName(activity.packageName)
        ApphudInternal.initialize(activity, apiKey, userId, deviceId, callback)
    }

    /**
     * Sets a listener for Apphud events.
     *
     * @param apphudListener The listener object that conforms to the ApphudListener interface.
     */
    fun setListener(apphudListener: ApphudListener) {
        ApphudInternal.apphudListener = apphudListener
    }

    /**
     * Updates the user ID. This method should only be called after the user is registered,
     * for example, inside the ApphudListener's userDidLoad method.
     *
     * @param userId The new user ID value to be set.
     */
    fun updateUserId(userId: UserId) = ApphudInternal.updateUserId(userId)

    /**
     * Retrieves the current user ID that identifies the user across multiple devices.
     *
     * @return The user ID.
     */
    fun userId(): UserId = ApphudInternal.userId

    /**
     * Retrieves the current device ID. This method is useful if you want to implement
     * a custom logout/login flow by saving the User ID and Device ID pair for each app user.
     *
     * @return The device ID.
     */
    fun deviceId(): String {
        return ApphudInternal.deviceId
    }

    //endregion
    //region === Placements, Paywalls and Products ===

    /**
     * Suspends the current coroutine until the placements from
     * Product Hub > Placements are available, potentially altered based on the
     * user's involvement in A/B testing, if applicable.
     * Method suspends until the inner `SkuDetails` are loaded from Google Play.
     *
     * A placement is a specific location within a user's journey
     * (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * If you want to obtain placements without waiting for `SkuDetails`
     * from Google Play, you can use `rawPlacements()` method.
     *
     * @return The list of `ApphudPlacement` objects.
     */
    suspend fun placements(): List<ApphudPlacement> =
        suspendCancellableCoroutine { continuation ->
            ApphudInternal.performWhenOfferingsPrepared {
                continuation.resume(ApphudInternal.placements)
            }
        }

    /**
     * Suspends the current coroutine until the specific placement by identifier
     * is available, potentially altered based on the
     * user's involvement in A/B testing, if applicable.
     * Method suspends until the inner `SkuDetails` are loaded from Google Play.
     *
     * A placement is a specific location within a user's journey
     * (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * If you want to obtain placements without waiting for `SkuDetails`
     * from Google Play, you can use `rawPlacements()` method.
     *
     * @return The list of `ApphudPlacement` objects.
     */
    suspend fun placement(identifier: String): ApphudPlacement? =
        placements().firstOrNull { it.identifier == identifier }

    /**
     * Returns the placements from Product Hub > Placements, potentially altered
     * based on the user's involvement in A/B testing, if applicable.
     *
     * A placement is a specific location within a user's journey
     * (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * If you want to obtain placements without waiting for `SkuDetails`
     * from Google Play, you can use `rawPlacements()` method.
     *
     * @param callback The callback function that is invoked with the list of `ApphudPlacement` objects.
     */
    fun placementsDidLoadCallback(callback: (List<ApphudPlacement>) -> Unit) {
        ApphudInternal.performWhenOfferingsPrepared { callback(ApphudInternal.placements) }
    }

    /** Returns:
     * List<ApphudPlacement>: A list of placements, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `SkuDetails`
     * are loaded from Google Play. That means placements may or may not have
     * inner Google Play products at the time you call this function.
     *
     * To get placements with awaiting for inner Google Play products, use
     * `placements()` or `placementsDidLoadCallback(...)` functions.
     */
    fun rawPlacements(): List<ApphudPlacement> = ApphudInternal.placements

    /**
     * Suspends the current coroutine until the paywalls from
     * Product Hub > Paywalls are available, potentially altered based on the
     * user's involvement in A/B testing, if applicable.
     *
     * Each paywall contains an array of `ApphudProduct` objects that
     * can be used for purchases.
     * `ApphudProduct` is Apphud's wrapper around `SkuDetails`.
     *
     * Method suspends until the inner `SkuDetails` are loaded from Google Play.
     *
     * If you want to obtain paywalls without waiting for `SkuDetails` from
     * Google Play, you can use `rawPaywalls()` method.
     *
     * @return The list of `ApphudPaywall` objects.
     */
    @Deprecated(
        "Deprecated in favor of Placements",
        ReplaceWith("this.placements()"),
    )
    suspend fun paywalls(): List<ApphudPaywall> =
        suspendCancellableCoroutine { continuation ->
            ApphudInternal.performWhenOfferingsPrepared {
                continuation.resume(ApphudInternal.paywalls)
            }
        }

    /**
     * Suspends the current coroutine until the specific paywall by identifier
     * is available, potentially altered based on the
     * user's involvement in A/B testing, if applicable.
     *
     * Each paywall contains an array of `ApphudProduct` objects that
     * can be used for purchases.
     * `ApphudProduct` is Apphud's wrapper around `SkuDetails`.
     *
     * Method suspends until the inner `SkuDetails` are loaded from Google Play.
     *
     * If you want to obtain paywalls without waiting for `SkuDetails` from
     * Google Play, you can use `rawPaywalls()` method.
     *
     * @return The list of `ApphudPaywall` objects.
     */
    @Deprecated(
        "Deprecated in favor of Placements",
        ReplaceWith("this.placement(identifier: String)"),
    )
    suspend fun paywall(identifier: String): ApphudPaywall? =
        paywalls().firstOrNull { it.identifier == identifier }

    /**
     * Returns the paywalls from Product Hub > Paywalls, potentially altered
     * based on the user's involvement in A/B testing, if applicable.
     *
     * Each paywall contains an array of `ApphudProduct` objects that
     * can be used for purchases.
     * `ApphudProduct` is Apphud's wrapper around `SkuDetails`.
     *
     * Method suspends until the inner `SkuDetails` are loaded from Google Play.
     *
     * If you want to obtain paywalls without waiting for `SkuDetails` from
     * Google Play, you can use `rawPaywalls()` method.
     *
     * @param callback The callback function that is invoked with the list of `ApphudPaywall` objects.
     */
    @Deprecated(
        "Deprecated in favor of Placements",
        ReplaceWith("this.placementsDidLoadCallback(callback)"),
    )
    fun paywallsDidLoadCallback(callback: (List<ApphudPaywall>) -> Unit) {
        ApphudInternal.performWhenOfferingsPrepared { callback(ApphudInternal.paywalls) }
    }

    /** Returns:
     * List<ApphudPaywall>: A list of paywalls, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `SkuDetails`
     * are loaded from Google Play. That means paywalls may or may not have
     * inner Google Play products at the time you call this function.
     *
     * To get paywalls with awaiting for inner Google Play products, use
     * Apphud.paywalls() or Apphud.paywallsDidLoadCallback(...) functions.
     */
    fun rawPaywalls(): List<ApphudPaywall> = ApphudInternal.paywalls

    /**
     * Call this method when your paywall screen is displayed to the user.
     * This is required for A/B testing analysis.
     *
     * @param paywall The `ApphudPaywall` object representing the paywall shown to the user.
     */
    fun paywallShown(paywall: ApphudPaywall) {
        ApphudInternal.paywallShown(paywall)
    }

    /**
     * Call this method when your paywall screen is dismissed without a purchase.
     * This is required for A/B testing analysis.
     *
     * @param paywall The `ApphudPaywall` object representing the paywall that was closed.
     */
    fun paywallClosed(paywall: ApphudPaywall) {
        ApphudInternal.paywallClosed(paywall)
    }

    /**
     * Returns permission groups configured in the Apphud dashboard under Product Hub > Products.
     * These groups are cached on the device.
     * Note that this method returns an empty array if `SkuDetails` are not yet fetched from Google Play.
     *
     * To get notified when `permissionGroups` are ready to use, you can use ApphudListener's
     * `apphudFetchProductsDetailsProducts` or `paywallsDidFullyLoad` methods, or `productsFetchCallback`.
     * When any of these methods is called, it indicates that `SkuDetails` are loaded and
     * the `permissionGroups` method is ready to use.
     *
     * Best practice is not to use this method directly but to use `paywalls()` instead.
     *
     * @return A list of `ApphudGroup` objects representing permission groups.
     */
    fun permissionGroups(): List<ApphudGroup> {
        return ApphudInternal.getPermissionGroups()
    }

    /**
     * Returns an array of `SkuDetails` objects, whose identifiers you added in Apphud > Product Hub > Products.
     * Note that this method will return empty array if products are not yet fetched.
     * To get notified when `products` are ready to use, implement `ApphudListener`'s
     * `apphudFetchProductsDetails` or `paywallsDidFullyLoad` methods, or use `productsFetchCallback`.
     * When any of these methods is called, it indicates that `SkuDetails` are loaded and
     * the `products` method is ready to use.
     * It is recommended not to use this method directly, but to use `paywalls()` instead.
     *
     * @return A list of `SkuDetails` objects, or null if not yet available.
     */
    @Deprecated(
        "Use \"paywalls()\" method instead.",
        ReplaceWith("this.paywalls()"),
    )
    fun products(): List<SkuDetails> {
        return ApphudInternal.getSkuDetails()
    }

    /**
     * This callback is triggered when `SkuDetails` are fetched from Google Play Billing.
     * Ensure that all product identifiers are added in Apphud > Product Hub > Products.
     * You can use this callback or implement `ApphudListener`'s `apphudFetchProductsDetails`
     * method, based on your preference.
     *
     * @param callback The callback function to be invoked with the list of `SkuDetails`.
     */
    @Deprecated(
        "Use \"paywalls()\" method instead.",
        ReplaceWith("this.paywalls()"),
    )
    fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit) {
        ApphudInternal.productsFetchCallback(callback)
    }

    /**
     * Returns the `SkuDetails` object for a specific product identifier.
     * Ensure the product identifier is added in Apphud > Product Hub > Products.
     * The method will return `null` if the product is not yet fetched from Google Play.
     *
     * @param productIdentifier The identifier of the product.
     * @return The `SkuDetails` object for the specified product, or null if not available.
     */
    @Deprecated(
        "Use \"paywalls()\" method instead.",
        ReplaceWith("this.paywalls()"),
    )
    fun product(productIdentifier: String): SkuDetails? {
        return ApphudInternal.getSkuDetailsByProductId(productIdentifier)
    }

    //endregion
    //region === Purchases ===

    /**
     * Determines if the user has active premium access, which includes any active subscription
     * or non-renewing purchase (lifetime).
     * Note: This method is not suitable for consumable in-app purchases, like coin packs.
     * Use this method to check if the user has active premium access. If you have consumable
     * purchases, consider using alternative methods, as this won't distinguish consumables
     * from non-consumables.
     *
     * @return `true` if the user has an active subscription or non-renewing purchase, `false` otherwise.
     */
    fun hasPremiumAccess(): Boolean {
        return hasActiveSubscription() || nonRenewingPurchases().firstOrNull { it.isActive() } != null
    }

    /**
     * Checks if the user has an active subscription. The information is cached on the device.
     * Use this method to determine whether the user has an active premium subscription.
     * Note: If you offer lifetime purchases, you must use the `isNonRenewingPurchaseActive` method.
     *
     * @return `true` if the user has an active subscription, `false` otherwise.
     */
    fun hasActiveSubscription(): Boolean = subscriptions().firstOrNull { it.isActive() } != null

    /**
     * Retrieves the subscription object that the current user has ever purchased.
     * Subscriptions are cached on the device.
     * Note: A non-null return value does not imply that the subscription is active.
     * Check `ApphudSdk.hasActiveSubscription()` or `subscription.isActive()` to determine
     * if the subscription should unlock premium functionality for the user.
     *
     * @return The `ApphudSubscription` object if available, `null` otherwise.
     */
    fun subscription(): ApphudSubscription? = subscriptions().firstOrNull()

    /**
     * Retrieves all the subscription objects that the user has ever purchased.
     * The information is cached on the device.
     *
     * @return A list of `ApphudSubscription` objects.
     */
    fun subscriptions(): List<ApphudSubscription> = ApphudInternal.currentUser?.subscriptions ?: listOf()

    /**
     * Retrieves all non-renewing product purchases that the user has ever made.
     * The information is cached on the device and sorted by purchase date.
     *
     * @return A list of `ApphudNonRenewingPurchase` objects.
     */
    fun nonRenewingPurchases(): List<ApphudNonRenewingPurchase> = ApphudInternal.currentUser?.purchases ?: listOf()

    /**
     * Checks if the current user has purchased a specific in-app product.
     * Returns `false` if the product is refunded or never purchased.
     * Note: This method considers the most recent purchase of the given product identifier.
     *
     * @param productId The identifier of the product to check.
     * @return `true` if the product is active, `false` otherwise.
     */
    fun isNonRenewingPurchaseActive(productId: ProductId): Boolean =
        nonRenewingPurchases().firstOrNull { it.productId == productId }?.isActive() ?: false

    /**
     * Initiates the purchase process for a specified product and automatically submits the
     * Google Play purchase token to Apphud.
     *
     * @param activity The current Activity context.
     * @param apphudProduct The `ApphudProduct` object representing the product to be purchased.
     * @param offerIdToken (Required for Subscriptions) The identifier of the offer for initiating the purchase. Developer should retrieve it from SubscriptionOfferDetails object.
     * @param oldToken (Optional) The Google Play Billing purchase token that the user is
     *                 upgrading or downgrading from.
     * @param replacementMode (Optional) The replacement mode for the subscription update.
     * @param consumableInAppProduct (Optional) Set to true for consumable products. Otherwise purchase will be treated as non-consumable and acknowledged.
     * @param block (Optional) A callback that returns an `ApphudPurchaseResult` object.
     */
    fun purchase(
        activity: Activity,
        apphudProduct: ApphudProduct,
        offerIdToken: String? = null,
        oldToken: String? = null,
        replacementMode: Int? = null,
        consumableInAppProduct: Boolean = false,
        block: ((ApphudPurchaseResult) -> Unit)?,
    ) = ApphudInternal.purchase(
        activity = activity,
        apphudProduct = apphudProduct,
        productId = null,
        offerIdToken = offerIdToken,
        oldToken = oldToken,
        replacementMode = replacementMode,
        consumableInappProduct = consumableInAppProduct,
        callback = block,
    )

    /**
     * Initiates the purchase process for a product by its Google Play product ID and automatically
     * submits the purchase token to Apphud.
     *
     * @param activity The current Activity context.
     * @param productId The Google Play product ID of the item to purchase.
     * @param offerIdToken (Required for Subscriptions) The identifier of the offer for initiating the purchase. Developer should retrieve it from SubscriptionOfferDetails object.
     * @param oldToken (Optional) The Google Play Billing purchase token that the user is
     *                 upgrading or downgrading from.
     * @param replacementMode (Optional) The replacement mode for the subscription update.
     *
     * @param consumableInAppProduct (Optional) Set to true for consumable products. Otherwise purchase will be treated as non-consumable and acknowledged.
     * @param block (Optional) A callback that returns an `ApphudPurchaseResult` object.
     */
    fun purchase(
        activity: Activity,
        productId: String,
        offerIdToken: String? = null,
        oldToken: String? = null,
        replacementMode: Int? = null,
        consumableInAppProduct: Boolean = false,
        block: ((ApphudPurchaseResult) -> Unit)?,
    ) = ApphudInternal.purchase(
        activity = activity,
        apphudProduct = null,
        productId = productId,
        offerIdToken = offerIdToken,
        oldToken = oldToken,
        replacementMode = replacementMode,
        consumableInappProduct = consumableInAppProduct,
        callback = block,
    )

    /**
     * Only for use in Observer Mode: call this method after every successful purchase.
     * Note: Passing the offerIdToken is mandatory for subscriptions!
     * This method submits the successful purchase information to Apphud.
     * Pass `paywallIdentifier` and `placementIdentifier` for A/B test analysis in Observer Mode.
     *
     * @param purchase The `Purchase` object representing the successful purchase.
     * @param skuDetails The `SkuDetails` object associated with the purchase.
     * @param offerIdToken The identifier of the subscription's offer token.
     * @param paywallIdentifier (Optional) The identifier of the paywall.
     * @param placementIdentifier (Optional) The identifier of the placement.
     */
    fun trackPurchase(
        purchase: Purchase,
        skuDetails: SkuDetails,
        offerIdToken: String?,
        paywallIdentifier: String? = null,
        placementIdentifier: String? = null,
    ) = ApphudInternal.trackPurchase(purchase, skuDetails, offerIdToken, paywallIdentifier, placementIdentifier)

    /**
     * Implements the 'Restore Purchases' mechanism. This method sends the current Play Market
     * Purchase Tokens to Apphud and returns subscription information.
     * Note: Even if the callback returns some subscription, it doesn't necessarily mean that
     * the subscription is active. Check `subscription.isActive()` for subscription status.
     *
     * @param callback Required. A callback that returns an array of subscriptions, in-app products,
     *                 or an optional error.
     */
    fun restorePurchases(callback: ApphudPurchasesRestoreCallback) {
        ApphudInternal.restorePurchases(callback)
    }

    /**
     * Refreshes the current entitlements, which includes subscriptions, promotional or non-renewing purchases.
     * To be notified about updates, listen for `ApphudListener`'s `apphudSubscriptionsUpdated` and
     * `apphudNonRenewingPurchasesUpdated` methods.
     * Note: Do not call this method on app launch, as Apphud SDK does it automatically.
     * It is best used when a promotional has been granted on the web or when the app reactivates
     * from the background, if needed.
     */
    fun refreshEntitlements() {
        ApphudInternal.refreshEntitlements()
    }

    /**
     * Quickly checks for active subscriptions or active lifetime purchases
     * associated with the current Google Play account.
     * This method is much faster than `Apphud.restorePurchases` as it bypasses validation,
     * providing a quick way to determine the presence of purchases.
     * If not empty, it can be used for skipping the initial paywall on the first app launch.
     * Note that `Apphud.hasPremiumAccess()` will still return false until
     * purchases are validated through Apphud, so this method should not be used for access control.
     *
     * __NOTE__: If any unvalidated purchases were found in the result of this method call,
     * Apphud will automatically track and validate them in the background,
     * so developer doesn't need to call `Apphud.restorePurchases` manually.
     */
    suspend fun unvalidatedActivePurchases(): List<Purchase> = ApphudInternal.restoreWithoutValidation()

    //endregion
    //region === Attribution ===

    /**
     * Collects device identifiers required for some third-party integrations (e.g., AppsFlyer, Adjust, Singular).
     * Identifiers include Advertising ID, Android ID, App Set ID.
     * Warning: When targeting Android 13 and above, declare the AD_ID permission in the manifest.
     * Be sure `optOutOfTracking()` is not called before this, otherwise identifiers will not be collected.
     */
    fun collectDeviceIdentifiers() {
        ApphudInternal.collectDeviceIdentifiers()
    }

    /**
     * Submits attribution data to Apphud from your attribution network provider.
     *
     * @param data Required. Attribution dictionary.
     * @param provider Required. Attribution provider name.
     * @param identifier Optional. Identifier that matches Apphud and the Attribution provider.
     */
    fun addAttribution(
        provider: ApphudAttributionProvider,
        data: Map<String, Any>? = null,
        identifier: String? = null,
    ) = ApphudInternal.addAttribution(provider, data, identifier)

    //endregion
    //region === User Properties ===

    /**
     * Sets a custom user property. The value must be one of the following types:
     * "Int", "Float", "Double", "Boolean", "String", or "null".
     *
     * Example:
     * Apphud.setUserProperty(ApphudUserPropertyKey.Email, "user@example.com")
     * Apphud.setUserProperty(ApphudUserPropertyKey.CustomProperty("custom_key"), 123)
     *
     * Note: Built-in keys have predefined value types:
     * "ApphudUserPropertyKey.Email": User email. Value must be String.
     * "ApphudUserPropertyKey.Name": User name. Value must be String.
     * "ApphudUserPropertyKey.Phone": User phone number. Value must be String.
     * "ApphudUserPropertyKey.Age": User age. Value must be Int.
     * "ApphudUserPropertyKey.Gender": User gender. Value must be one of: "male", "female", "other".
     * "ApphudUserPropertyKey.Cohort": User install cohort. Value must be String.
     *
     * @param key The property key, either custom or built-in.
     * @param value The property value, or "null" to remove the property.
     * @param setOnce If set to "true", the property cannot be updated later.
     */
    fun setUserProperty(
        key: ApphudUserPropertyKey,
        value: Any?,
        setOnce: Boolean = false,
    ) {
        ApphudInternal.setUserProperty(key = key, value = value, setOnce = setOnce, increment = false)
    }

    /**
     * Increments a custom user property. The value to increment must be one of the types:
     * "Int", "Float", or "Double".
     *
     * Example:
     * Apphud.incrementUserProperty(ApphudUserPropertyKey.CustomProperty("progress"), 10)
     *
     * @param key The property key, which should be a custom key.
     * @param by The value to increment the property by. Negative values will decrement.
     */
    fun incrementUserProperty(
        key: ApphudUserPropertyKey,
        by: Any,
    ) {
        ApphudInternal.setUserProperty(key = key, value = by, setOnce = false, increment = true)
    }

    //endregion
    //region === Other ===

    /**
     * Grants a free promotional subscription to the user.
     * Returns `true` in the callback if the promotional subscription was successfully granted.
     *
     * Note: Either pass `productId` or `permissionGroup`, or pass both as null.
     * If both are provided, `productId` will be used.
     *
     * @param daysCount The number of days for the free premium access. For a lifetime promotion, pass a large number.
     * @param productId (Optional) The product ID of the subscription for the promotion.
     * @param permissionGroup (Optional) The permission group for the subscription. Use when you have multiple groups.
     * @param callback (Optional) Returns `true` if the promotional subscription was granted.
     */
    fun grantPromotional(
        daysCount: Int,
        productId: String?,
        permissionGroup: ApphudGroup? = null,
        callback: ((Boolean) -> Unit)? = null,
    ) {
        ApphudInternal.grantPromotional(daysCount, productId, permissionGroup, callback)
    }

    /**
     * Enables debug logs. It is recommended to call this method before SDK initialization.
     */
    fun enableDebugLogs() = ApphudUtils.enableDebugLogs()

    /**
     * Use this method if you have a custom login system with your own backend logic.
     * It effectively logs out the current user in the context of the Apphud SDK.
     */
    fun logout() = ApphudInternal.logout()

    /**
     * Must be called before SDK initialization. If called, certain user parameters
     * like Advertising ID, Android ID, App Set ID, Device Type, IP address will not be tracked by Apphud.
     */
    fun optOutOfTracking() {
        ApphudUtils.optOutOfTracking = true
    }
    //endregion
}
