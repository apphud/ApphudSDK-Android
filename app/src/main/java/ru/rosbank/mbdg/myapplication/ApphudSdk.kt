package ru.rosbank.mbdg.myapplication

import android.content.Context
import ru.rosbank.mbdg.myapplication.domain.ApphudSubscription

object ApphudSdk {

    fun init(context: Context) {
        ApphudInternal.context = context
    }

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    fun start(apiKey: ApiKey) = startManually(apiKey, null, null)

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    fun start(apiKey: ApiKey, userId: UserId) = startManually(apiKey, userId, null)

    /**
     * Initializes Apphud SDK with Device ID parameter. Not recommended for use unless you know what you are doing.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     * - parameter deviceID: Optional. You can provide your own unique device identifier. If null passed then UUID will be generated instead.
     */
    fun startManually(apiKey: ApiKey, userId: UserId? = null, deviceId: DeviceId? = null) =
        ApphudInternal.initialize(apiKey, userId, deviceId)

    /**
     * Updates user ID value
     *
     * - parameter userID: Required. New user ID value.
     */
    fun updateUser(id: UserId) = Unit

    /**
     * Returns current userID that identifies user across his multiple devices.
     */
    fun userId(): UserId = TODO("Not implemented")

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
    fun subscription(): ApphudSubscription? = null
}