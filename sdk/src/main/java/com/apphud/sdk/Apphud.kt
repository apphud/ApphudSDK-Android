package com.apphud.sdk

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max

object Apphud {
    //region === Initialization ===

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @param context The application context.
     * @param apiKey Your API key. This is a required parameter.
     * @param observerMode (Optional). Pass true if you're using SDK only in analytics mode and not operating with Paywalls and Placements.
     * @param callback (Optional) A callback function that is invoked with the `ApphudUser`
     *                 object after the SDK initialization is complete. __Note__: Do not store
     *                 `ApphudUser`
     *                 instance in your own code, since it may change at runtime.
     */
    fun start(
        context: Context,
        apiKey: ApiKey,
        observerMode: Boolean = false,
        callback: ((ApphudUser) -> Unit)? = null,
    ) = start(context, apiKey, null,  null, observerMode, callback)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @param context The application context.
     * @param apiKey Your API key. This is a required parameter.
     * @param userId (Optional) A unique user identifier. If null is passed, a UUID will be
     *               generated and used as the user identifier.
     * @param observerMode (Optional). Pass true if you're using SDK only in analytics mode and not operating with Paywalls and Placements.
     * @param callback (Optional) A callback function that is invoked with the `ApphudUser`
     *                 object after the SDK initialization is complete. __Note__: Do not store
     *                 `ApphudUser`
     *                 instance in your own code, since it may change at runtime.
     */
    fun start(
        context: Context,
        apiKey: ApiKey,
        userId: UserId? = null,
        observerMode: Boolean = false,
        callback: ((ApphudUser) -> Unit)? = null,
    ) = start(context, apiKey, userId, null, observerMode, callback)

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
     * @param observerMode (Optional). Pass true if you're using SDK only in analytics mode and not operating with Paywalls and Placements.
     * @param callback (Optional) A callback function that is invoked with the `ApphudUser`
     *                 object after the SDK initialization is complete. __Note__: Do not store
     *                 `ApphudUser`
     *                 instance in your own code, since it may change at runtime.
     */
    fun start(
        context: Context,
        apiKey: ApiKey,
        userId: UserId? = null,
        deviceId: DeviceId? = null,
        observerMode: Boolean = false,
        callback: ((ApphudUser) -> Unit)? = null,
    ) {
        ApphudUtils.setPackageName(context.packageName)
        ApphudInternal.initialize(context, apiKey, userId, deviceId, observerMode, callback)
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
     * Method suspends until the inner `ProductDetails` are loaded from Google Play.
     *
     * This is equivalent to `fetchPlacements(callback: (List<ApphudPlacement>, ApphudError?) -> Unit)`.
     *
     * A placement is a specific location within a user's journey
     * (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * If you want to obtain placements without waiting for `ProductDetails`
     * from Google Play, you can use `rawPlacements()` method.
     *
     * @param preferredTimeout The approximate duration, in seconds, after which the SDK will cease
     * retry attempts to Apphud backend in case of failures and return an error.
     * The default and minimum value for this parameter is 10.0 seconds.
     * This parameter doesn't affect fetching products from Google Play.
     * @return The list of `ApphudPlacement` objects.
     */
    suspend fun placements(preferredTimeout: Double = APPHUD_DEFAULT_MAX_TIMEOUT): List<ApphudPlacement> =
        suspendCancellableCoroutine { continuation ->
            fetchPlacements(preferredTimeout = preferredTimeout) { _, _ ->
                /* Error is not returned is suspending function.
                    If you want to handle error, use `fetchPlacements` method.
                */
                continuation.resume(ApphudInternal.placements)
            }
        }

    /**
     * Suspends the current coroutine until the specific placement by identifier
     * is available, potentially altered based on the
     * user's involvement in A/B testing, if applicable.
     * Method suspends until the inner `ProductDetails` are loaded from Google Play.
     *
     * A placement is a specific location within a user's journey
     * (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * If you want to obtain placements without waiting for `ProductDetails`
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
     * __Note:__ Method waits until the inner `ProductDetails` are loaded from Google Play.
     *
     * This is equivalent to `suspend fun placements()` method.
     *
     * A placement is a specific location within a user's journey
     * (such as onboarding, settings, etc.) where its internal paywall
     * is intended to be displayed.
     *
     * If you want to obtain placements without waiting for `ProductDetails`
     * from Google Play, you can use `rawPlacements()` method.
     *
     * __IMPORTANT:__ The callback may return both placements and an error simultaneously.
     * If there is an issue with Google Billing and inner product details could not be fetched,
     * an error will be returned along with the raw placements array.
     * This allows for handling situations where partial data is available.
     *
     * @param preferredTimeout The approximate duration, in seconds, after which the SDK will cease
     * retry attempts to Apphud backend in case of failures and return an error.
     * The default and minimum value for this parameter is 10.0 seconds.
     * This parameter doesn't affect fetching products from Google Play.
     * @param callback The callback function that is invoked with the list of `ApphudPlacement` objects.
     * Second parameter in callback represents optional error, which may be
     * on Google (BillingClient issue) or Apphud side.
     *
     */
    fun fetchPlacements(preferredTimeout: Double = APPHUD_DEFAULT_MAX_TIMEOUT, callback: (List<ApphudPlacement>, ApphudError?) -> Unit) {
        ApphudInternal.performWhenOfferingsPrepared(preferredTimeout = preferredTimeout) { callback(ApphudInternal.placements, it) }
    }

    /** Returns:
     * List<ApphudPlacement>: A list of placements, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `ProductDetails`
     * are loaded from Google Play. That means placements may or may not have
     * inner Google Play products at the time you call this function.
     *
     * To get placements with awaiting for inner Google Play products, use
     * `placements()` or `placementsDidLoadCallback(...)` functions.
     */
    fun rawPlacements(): List<ApphudPlacement> = ApphudInternal.placements

    /** Returns:
     * List<ApphudPaywall>: A list of paywalls, potentially altered based
     * on the user's involvement in A/B testing, if any.
     *
     * __Note__: This function doesn't suspend until inner `ProductDetails`
     * are loaded from Google Play. That means paywalls may or may not have
     * inner Google Play products at the time you call this function.
     *
     * To get paywalls with awaiting for inner Google Play products, use
     * Apphud.paywalls() or Apphud.paywallsDidLoadCallback(...) functions.
     */
    fun rawPaywalls(): List<ApphudPaywall> = ApphudInternal.paywalls

    /**
     * Disables automatic paywall and placement requests during the SDK's initial setup.
     * Developers must explicitly call `fetchPlacements` or `placements()` methods
     * at a later point in the app's lifecycle to fetch placements with inner paywalls.
     * Example:
     * ```
     * Apphud.start(context, api_key)
     * Apphud.deferPlacements()
     * ...
     * Apphud.fetchPlacements { placements, error ->
     * // Handle fetched placements
     * }
     * ```
     *
     * Note: You can use this method alongside `forceFlushUserProperties` to achieve
     * real-time user segmentation based on custom user properties.
     */
    fun deferPlacements() {
        ApphudInternal.deferPlacements = true
    }

    /**
     * Returns the paywalls from Product Hub > Paywalls, potentially altered
     * based on the user's involvement in A/B testing, if applicable.
     * __Note:__ Method waits until the inner `ProductDetails` are loaded from Google Play.
     *
     * This is equivalent to `suspend fun paywalls()` method.
     *
     * Each paywall contains an array of `ApphudProduct` objects that
     * can be used for purchases.
     * `ApphudProduct` is Apphud's wrapper around `ProductDetails`.
     *
     * If you want to obtain paywalls without waiting for `ProductDetails` from
     * Google Play, you can use `rawPaywalls()` method.
     *
     * __IMPORTANT:__ The callback may return both paywalls and an error simultaneously.
     * If there is an issue with Google Billing and inner product details could not be fetched,
     * an error will be returned along with the raw paywalls array.
     * This allows for handling situations where partial data is available.
     *
     * @param preferredTimeout The approximate duration, in seconds, after which the SDK will cease
     * retry attempts to Apphud backend in case of failures and return an error.
     * The default and minimum value for this parameter is 10.0 seconds.
     * This parameter doesn't affect fetching products from Google Play.
     * @param callback The callback function that is invoked with the list of `ApphudPaywall` objects.
     * Second parameter in callback represents optional error, which may be
     * on Google (BillingClient issue) or Apphud side.
     */
    @Deprecated(
        "Deprecated in favor of Placements",
        ReplaceWith("this.placementsDidLoadCallback(callback)"),
    )
    fun paywallsDidLoadCallback(preferredTimeout: Double = APPHUD_DEFAULT_MAX_TIMEOUT, callback: (List<ApphudPaywall>, ApphudError?) -> Unit) {
        ApphudInternal.performWhenOfferingsPrepared(preferredTimeout = preferredTimeout) { callback(ApphudInternal.paywalls, it) }
    }

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
     * Asynchronously fetches permission groups configured in the Apphud > Product Hub.
     * Groups are cached on the device.
     *
     * @return A list of `ApphudGroup` objects representing permission groups.
     */
    suspend fun permissionGroups(): List<ApphudGroup> {
        return ApphudInternal.getPermissionGroups()
    }

    /**
     * Returns an array of `ProductDetails` objects, whose identifiers you added in Apphud > Product Hub > Products.
     * Note that this method will return empty array if products are not yet fetched.
     * To get notified when `products` are ready to use, implement `ApphudListener`'s
     * `apphudFetchProductsDetails` or `paywallsDidFullyLoad` methods, or use `productsFetchCallback`.
     * When any of these methods is called, it indicates that `ProductDetails` are loaded and
     * the `products` method is ready to use.
     * It is recommended not to use this method directly, but to use `paywalls()` instead.
     *
     * @return A list of `ProductDetails` objects, or null if not yet available.
     */
    @Deprecated(
        "Use \"paywalls()\" method instead.",
        ReplaceWith("this.paywalls()"),
    )
    fun products(): List<ProductDetails> {
        return ApphudInternal.getProductDetails()
    }

    /**
     * This callback is triggered when `ProductDetails` are fetched from Google Play Billing.
     * Ensure that all product identifiers are added in Apphud > Product Hub > Products.
     * You can use this callback or implement `ApphudListener`'s `apphudFetchProductsDetails`
     * method, based on your preference.
     *
     * @param callback The callback function to be invoked with the list of `ProductDetails`.
     */
    @Deprecated(
        "Use \"paywalls()\" method instead.",
        ReplaceWith("this.paywalls()"),
    )
    fun productsFetchCallback(callback: (List<ProductDetails>) -> Unit) {
        ApphudInternal.productsFetchCallback(callback)
    }

    /**
     * Returns the `ProductDetails` object for a specific product identifier.
     * Ensure the product identifier is added in Apphud > Product Hub > Products.
     * The method will return `null` if the product is not yet fetched from Google Play.
     *
     * @param productIdentifier The identifier of the product.
     * @return The `ProductDetails` object for the specified product, or null if not available.
     */
    @Deprecated(
        "Use \"paywalls()\" method instead.",
        ReplaceWith("this.paywalls()"),
    )
    fun product(productIdentifier: String): ProductDetails? {
        return ApphudInternal.getProductDetailsByProductId(productIdentifier)
    }

    //endregion
    //region === Purchases ===

    /**
     * Determines if the user has active premium access, which includes any active subscription
     * or non-renewing purchase (lifetime).
     *
     * @return `true` if the user has an active subscription or non-renewing purchase, `false` otherwise.
     */
    fun hasPremiumAccess(): Boolean {
        return hasActiveSubscription() || nonRenewingPurchases().firstOrNull { it.isActive() && !it.isConsumable } != null
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
     * @param offerIdToken (Required for Subscriptions) The identifier of the offer for initiating the purchase.
     *                                                  Developer should retrieve it from SubscriptionOfferDetails array.
     *                                                  If not passed, then SDK will try to use first one from the array.
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
     * Tracks a purchase made through Google Play. This method should be used only in Observer Mode,
     * specifically when utilizing Apphud Paywalls and Placements, and when you need to associate the
     * purchase with specific paywall and placement identifiers.
     *
     * In all other cases, purchases will be automatically intercepted and sent to Apphud.
     *
     * Note: The `offerIdToken` is mandatory for subscriptions. The `paywallIdentifier` and `placementIdentifier`
     * are optional but recommended for A/B test analysis in Observer Mode.
     *
     * @param productId The Google Play product ID of the item to purchase.
     * @param offerIdToken The identifier of the subscription's offer token. This parameter is required for subscriptions.
     * @param paywallIdentifier (Optional) The identifier of the paywall.
     * @param placementIdentifier (Optional) The identifier of the placement.
     */
    fun trackPurchase(
        productId: String,
        offerIdToken: String?,
        paywallIdentifier: String? = null,
        placementIdentifier: String? = null,
    ) = ApphudInternal.trackPurchase(productId, offerIdToken, paywallIdentifier, placementIdentifier)

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
     * Refreshes current user data, which includes:
     * paywalls, placements, subscriptions, non-renewing purchases, or promotionals.
     *
     * To be notified about updates, listen for `ApphudListener`'s `apphudSubscriptionsUpdated` and
     * `apphudNonRenewingPurchasesUpdated` methods.
     *
     * __NOTE__: Do not call this method on app launch, as Apphud SDK does it automatically.
     *
     * You can call this method, when the app reactivates from the background, if needed.
     */
    fun refreshUserData() {
        ApphudInternal.refreshEntitlements(forceRefresh = true)
    }

    /**
     * Retrieves Google Play's native Purchase objects,
     * which may include only active subscriptions or active non-consumed one-time purchases.
     * Compared to `Apphud.restorePurchases`, this method offers a quicker way
     * to determine the presence of owned purchases as it bypasses validation by Apphud.
     *
     * Returns `BillingClient.BillingResponseCode` as second parameter of Pair.
     *
     * Usage of this function for granting premium access is not advised,
     * as these purchases may not yet be validated.
     *
     * __NOTE__: `Apphud.hasPremiumAccess()` may return false until
     * purchases are validated by Apphud.
     *
     * __NOTE__: If any native purchases were found in the result of this method call,
     * Apphud will automatically track and validate them in the background,
     * so developer doesn't need to call `Apphud.restorePurchases` afterwards.
     */
    suspend fun nativePurchases(forceRefresh: Boolean = false): Pair<List<Purchase>, Int> = ApphudInternal.fetchNativePurchases(forceRefresh = forceRefresh)

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
     * This method sends all user properties immediately to Apphud.
     * Should be used for audience segmentation in placements based on user properties.
     *
     *
     * Example:
     *     ````
     *     Apphud.start(context, api_key)
     *     Apphud.deferPlacements()
     *     Apphud.setUserProperty(ApphudUserPropertyKey.CustomProperty("some_key"), "some_value")
     *
     *     Apphud.forceFlushUserProperties { result ->
     *        // now placements will respect user properties that have been sent previously
     *        Apphud.fetchPlacements { placements, error ->
     *          // handle placements
     *        }
     *     }
     *     ```
     */
    fun forceFlushUserProperties(completion: ((Boolean) -> Unit)?) {
        ApphudInternal.forceFlushUserProperties(true, completion)
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

    /**
     * Returns `true` if fallback mode is on.
     * That means paywalls are loaded from the fallback json file.
     */
    fun isFallbackMode(): Boolean {
        return ApphudInternal.fallbackMode
    }

    /**
     * Explicitly loads fallback paywalls from the json file, if it was added to the project assets.
     * By default, SDK automatically tries to load paywalls from the JSON file, if possible.
     * However, developer can also call this method directly for more control.
     * For more details, visit https://docs.apphud.com/docs/paywalls#set-up-fallback-mode
     */
    fun loadFallbackPaywalls(callback: (List<ApphudPaywall>?, ApphudError?) -> Unit) {
        ApphudInternal.processFallbackData(callback)
    }

    /**
     * Must be called before SDK initialization.
     * Will make SDK to disregard cache and force refresh paywalls and placements.
     * Call it only if keeping paywalls and placements up to date is critical for your app business.
     */
    fun invalidatePaywallsCache() {
        ApphudInternal.ignoreCache = true
    }
    //endregion
}
