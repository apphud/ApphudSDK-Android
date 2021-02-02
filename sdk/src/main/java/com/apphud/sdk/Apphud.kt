package com.apphud.sdk

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
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

    private fun isMainProcess(context: Context): Boolean =
        context.packageName == getProcessName(context)

    private fun getProcessName(context: Context): String? {
        val mypid = Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return manager?.let {
            val infos = manager.runningAppProcesses
            for (info in infos) {
                if (info.pid == mypid) {
                    return@let info.processName
                }
            }
            null
        }
    }

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * @parameter apiKey: Required. Your api key.
     * @parameter userId: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     * @parameter deviceID: Optional. You can provide your own unique device identifier. If null passed then UUID will be generated instead.
     */
    @kotlin.jvm.JvmStatic
    fun start(
        context: Context,
        apiKey: ApiKey,
        userId: UserId? = null,
        deviceId: DeviceId? = null
    ) {
        if (isMainProcess(context)) {
            ApphudInternal.apiKey = apiKey
            ApphudInternal.context = context
            ApphudInternal.loadAdsId()
            ApphudInternal.registration(userId, deviceId)
            Log.d("APP_HUD", "start SDK")
        } else {
            Log.d("APP_HUD", "will not start - only main process is supported by SDK")
        }
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
     * Purchases product and automatically submit
     * @activity: current Activity for use
     * @productId: The identifier of the product you wish to purchase
     * @block: The closure that will be called when purchase completes.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, productId: String, block: (List<Purchase>) -> Unit) =
        ApphudInternal.purchase(activity, productId, block)

    /**
     * Purchases product and automatically submit
     * @activity: current Activity for use
     * @details: The skuDetails of the product you wish to purchase
     * @block: The closure that will be called when purchase completes.
     */
    @kotlin.jvm.JvmStatic
    fun purchase(activity: Activity, details: SkuDetails, block: (List<Purchase>) -> Unit) =
            ApphudInternal.purchase(activity, details, block)
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