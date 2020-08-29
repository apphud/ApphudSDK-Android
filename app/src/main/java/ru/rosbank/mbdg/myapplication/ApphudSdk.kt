package ru.rosbank.mbdg.myapplication

import android.content.Context
import ru.rosbank.mbdg.myapplication.domain.ApphudNonRenewingPurchase
import ru.rosbank.mbdg.myapplication.domain.ApphudSubscription

object ApphudSdk {

    fun init(context: Context, apiKey: ApiKey) {
        ApphudInternal.apiKey = apiKey
        ApphudInternal.context = context
    }

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    fun start() = startManually(null, null)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    fun start(userId: UserId) = startManually(userId, null)

    /**
     * Initializes Apphud SDK with Device ID parameter. Not recommended for use unless you know what you are doing.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     * - parameter deviceID: Optional. You can provide your own unique device identifier. If null passed then UUID will be generated instead.
     */
    fun startManually(userId: UserId? = null, deviceId: DeviceId? = null) =
        ApphudInternal.registration(userId, deviceId)

    /**
     * Updates user ID value
     *
     * - parameter userId: Required. New user ID value.
     */
    fun updateUserId(userId: UserId) = ApphudInternal.updateUserId(userId)

    /**
     * Returns current userID that identifies user across his multiple devices.
     */
    fun userId(): UserId = ApphudInternal.userId

    /**
     * Returns true if user has active subscription.
     * Use this method to determine whether or not to unlock premium functionality to the user.
     */
    fun hasActiveSubscription(): Boolean = subscription()?.isActive() ?: false

    /**
     * Returns subscription object that current user has ever purchased. Subscriptions are cached on device.
     * Note: If returned object is not null, it doesn't mean that subscription is active.
     * You should check `ApphudSdk.hasActiveSubscription()` method or `subscription.isActive()`
     *      value to determine whether or not to unlock premium functionality to the user.
     */
    fun subscription(): ApphudSubscription? =
        ApphudInternal.currentUser?.subscriptions?.firstOrNull()

    /**
     * Returns an array of all subscriptions that this user has ever purchased. Subscriptions are cached on device.
     * Use this method if you have more than one subsription group in your app.
     */
    fun subscriptions(): List<ApphudSubscription> =
        ApphudInternal.currentUser?.subscriptions ?: emptyList()

    /**
     * Returns an array of all standard in-app purchases (consumables, nonconsumables or nonrenewing subscriptions)
     * that this user has ever purchased. Purchases are cached on device. This array is sorted by purchase date.
     * Apphud only tracks consumables if they were purchased after integrating Apphud SDK.
     */
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
    fun isNonRenewingPurchaseActive(productId: ProductId): Boolean =
        ApphudInternal.currentUser?.purchases
            ?.firstOrNull { it.productId == productId }?.isActive() ?: false

    /**
     * Enables debug logs. Better to call this method before SDK initialization.
     */
    fun enableDebugLogs() = ApphudUtils.enableDebugLogs()
}