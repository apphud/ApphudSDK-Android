package ru.rosbank.mbdg.myapplication

object ApphudSdk {

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    fun start(apiKey: ApiKey) = Unit

    /**
     * Initializes Apphud SDK. You should call it during app launch.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     */
    fun start(apiKey: ApiKey, userId: UserId) = Unit

    /**
     * Initializes Apphud SDK with Device ID parameter. Not recommended for use unless you know what you are doing.
     *
     * - parameter apiKey: Required. Your api key.
     * - parameter userID: Optional. You can provide your own unique user identifier. If null passed then UUID will be generated instead.
     * - parameter deviceID: Optional. You can provide your own unique device identifier. If null passed then UUID will be generated instead.
     */
    fun startManually(apiKey: ApiKey, userId: UserId? = null, deviceId: DeviceId? = null) = Unit

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
}