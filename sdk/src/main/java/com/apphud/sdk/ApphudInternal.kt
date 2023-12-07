package com.apphud.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.body.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.managers.RequestManager.applicationContext
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {
    //region === Variables ===
    internal val mainScope = CoroutineScope(Dispatchers.Main)
    internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val errorHandler =
        CoroutineExceptionHandler { context, error ->
            error.message?.let { ApphudLog.logE(it) }
        }

    internal const val ERROR_TIMEOUT = 408
    internal val FALLBACK_ERRORS = listOf(ERROR_TIMEOUT, 500, 502, 503)

    internal lateinit var billing: BillingWrapper
    internal val storage by lazy { SharedPreferencesStorage.getInstance(context) }
    internal var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
    internal var productDetails = mutableListOf<ProductDetails>()
    internal var paywalls = listOf<ApphudPaywall>()
    internal var placements = listOf<ApphudPlacement>()

    internal var didLoadOfferings = false

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val pendingUserProperties = mutableMapOf<String, ApphudUserProperty>()
    private val userPropertiesRunnable =
        Runnable {
            if (currentUser != null) {
                updateUserProperties()
            } else {
                setNeedsToUpdateUserProperties = true
            }
        }

    private var setNeedsToUpdateUserProperties: Boolean = false
        set(value) {
            field = value
            if (value) {
                handler.removeCallbacks(userPropertiesRunnable)
                handler.postDelayed(userPropertiesRunnable, 1000L)
            } else {
                handler.removeCallbacks(userPropertiesRunnable)
            }
        }

    private const val MUST_REGISTER_ERROR = " :You must call `Apphud.start` method before calling any other methods."
    private var generatedUUID = UUID.randomUUID().toString()
    internal var productGroups: MutableList<ApphudGroup> = mutableListOf()
    private var allowIdentifyUser = true
    internal var didRegisterCustomerAtThisLaunch = false
    private var is_new = true
    private lateinit var apiKey: ApiKey
    lateinit var deviceId: DeviceId

    internal var fallbackMode = false
    internal lateinit var userId: UserId
    internal lateinit var context: Context
    internal var currentUser: ApphudUser? = null
    internal var apphudListener: ApphudListener? = null

    private var customProductsFetchedBlock: ((List<ProductDetails>) -> Unit)? = null
    private var offeringsPreparedCallbacks = mutableListOf<(() -> Unit)>()
    private var userRegisteredBlock: ((ApphudUser) -> Unit)? = null
    private var lifecycleEventObserver =
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (fallbackMode) {
                        storage.isNeedSync = true
                    }
                    ApphudLog.log("Application stopped [need sync ${storage.isNeedSync}]")
                }
                Lifecycle.Event.ON_START -> {
                    // do nothing
                }
                Lifecycle.Event.ON_CREATE -> {
                    // do nothing
                }
                else -> {}
            }
        }
    //endregion

    //region === Start ===
    internal fun initialize(
        context: Context,
        apiKey: ApiKey,
        userId: UserId?,
        deviceId: DeviceId?,
        callback: ((ApphudUser) -> Unit)?,
    ) {
        if (!allowIdentifyUser) {
            ApphudLog.logE(
                " " +
                    "\n=============================================================" +
                    "\nAbort initializing, because Apphud SDK already initialized." +
                    "\nYou can only call `Apphud.start()` once per app lifecycle." +
                    "\nOr if `Apphud.logout()` was called previously." +
                    "\n=============================================================",
            )
            return
        }

        mainScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }

        ApphudLog.log("Start initialization with userId=$userId, deviceId=$deviceId")
        if (apiKey.isEmpty()) throw Exception("ApiKey can't be empty")

        this.context = context
        this.apiKey = apiKey
        billing = BillingWrapper(context)

        val needRegistration = needRegistration(userId)

        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)
        RequestManager.setParams(this.context, this.userId, this.deviceId, this.apiKey)

        allowIdentifyUser = false
        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")

        this.currentUser = storage.apphudUser
        this.productGroups = readGroupsFromCache()
        /**
         * We cannot get paywalls and placements from current user,
         * because currentUser doesn't have cache timeout because it has
         * purchases history
         *
         * But paywalls and placements must have cache timeout
         */
        this.paywalls = readPaywallsFromCache()
        this.placements = readPlacementsFromCache()
        this.userRegisteredBlock = callback

        Log.d("Apphud", "Paywalls from cache: ${this.paywalls.count()}")

        loadProducts()

        if (needRegistration) {
            registration(this.userId, this.deviceId, true, null)
        } else {
            mainScope.launch {
                notifyLoadingCompleted(storage.apphudUser, null, true)
            }
        }
    }

    internal fun refreshEntitlements(forceRefresh: Boolean = false) {
        if (didRegisterCustomerAtThisLaunch || forceRefresh) {
            ApphudLog.log("RefreshEntitlements: didRegister:$didRegisterCustomerAtThisLaunch force:$forceRefresh")
            registration(this.userId, this.deviceId, true, null)
        }
    }
    //endregion

    //region === Registration ===
    private fun needRegistration(passedUserId: String?): Boolean {
        passedUserId?.let {
            if (!storage.userId.isNullOrEmpty()) {
                if (it != storage.userId) {
                    return true
                }
            }
        }
        if (storage.userId.isNullOrEmpty() ||
            storage.deviceId.isNullOrEmpty() ||
            storage.apphudUser == null ||
            storage.paywalls == null ||
            storage.needRegistration()
        ) {
            return true
        }
        return false
    }

    private var notifyFullyLoaded = false

    @Synchronized
    internal fun notifyLoadingCompleted(
        customerLoaded: ApphudUser? = null,
        productDetailsLoaded: List<ProductDetails>? = null,
        fromCache: Boolean = false,
        fromFallback: Boolean = false,
    ) {
        var paywallsPrepared = true

        productDetailsLoaded?.let {
            productGroups = readGroupsFromCache()
            updateGroupsWithProductDetails(productGroups)

            // notify that productDetails are loaded
            apphudListener?.apphudFetchProductDetails(getProductDetailsList())
            customProductsFetchedBlock?.invoke(getProductDetailsList())
        }

        customerLoaded?.let {
            var updateOfferingsFromCustomer = false

            if (fromCache || fromFallback) {
                notifyFullyLoaded = true
            } else {
                if (it.paywalls.isNotEmpty()) {
                    notifyFullyLoaded = true
                    updateOfferingsFromCustomer = true
                    cachePaywalls(it.paywalls)
                    cachePlacements(it.placements)
                } else {
                    /* Attention:
                     * If customer loaded without paywalls, do not reload paywalls from cache!
                     * If cache time is over, paywall from cache will be NULL
                     */
                    paywallsPrepared = false
                }
                storage.updateCustomer(it, apphudListener)
            }

            if (updateOfferingsFromCustomer) {
                paywalls = it.paywalls
                placements = it.placements
            } else if (paywallsPrepared || fromFallback) {
                paywalls = readPaywallsFromCache()
                placements = readPlacementsFromCache()
            }

            currentUser = it
            userId = it.userId

            // TODO: should be called only if something changed
            apphudListener?.apphudNonRenewingPurchasesUpdated(currentUser!!.purchases)
            apphudListener?.apphudSubscriptionsUpdated(currentUser!!.subscriptions)

            if (!didRegisterCustomerAtThisLaunch) {
                apphudListener?.userDidLoad(paywalls, placements)
                this.userRegisteredBlock?.invoke(it)
                this.userRegisteredBlock = null

                if (it.isTemporary == false && !fallbackMode) {
                    didRegisterCustomerAtThisLaunch = true
                }
            }

            if (!fromFallback && fallbackMode) {
                disableFallback()
            }
        }

        updatePaywallsAndPlacements()

        if (paywallsPrepared && currentUser != null && paywalls.isNotEmpty() && productDetails.isNotEmpty() && notifyFullyLoaded) {
            notifyFullyLoaded = false
            if (!didLoadOfferings) {
                didLoadOfferings = true
                apphudListener?.paywallsDidFullyLoad(paywalls)
                apphudListener?.placementsDidFullyLoad(placements)
                offeringsPreparedCallbacks.forEach { it.invoke() }
                offeringsPreparedCallbacks.clear()
            }
        }
    }

    private val mutex = Mutex()

    private fun registration(
        userId: UserId,
        deviceId: DeviceId,
        forceRegistration: Boolean = false,
        completionHandler: ((ApphudUser?, ApphudError?) -> Unit)?,
    ) {
        coroutineScope.launch(errorHandler) {
            mutex.withLock {
                if (currentUser == null || forceRegistration) {
                    ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")
                    ApphudLog.log(
                        "Registration conditions: user_is_null=${currentUser == null}, forceRegistration=$forceRegistration isTemporary=${currentUser?.isTemporary}",
                    )

                    RequestManager.registration(!didRegisterCustomerAtThisLaunch, is_new, forceRegistration) { customer, _ ->
                        customer?.let {
                            currentUser = it
                            storage.lastRegistration = System.currentTimeMillis()

                            mainScope.launch {
                                notifyLoadingCompleted(it)
                                completionHandler?.invoke(it, null)

                                if (pendingUserProperties.isNotEmpty() && setNeedsToUpdateUserProperties) {
                                    updateUserProperties()
                                }
                            }

                            if (storage.isNeedSync) {
                                coroutineScope.launch(errorHandler) {
                                    ApphudLog.log("Registration: isNeedSync true, start syncing")
                                    syncPurchases()
                                }
                            }
                        } ?: run {
                            ApphudLog.logE("Registration: error")
                            mainScope.launch {
                                completionHandler?.invoke(
                                    null,
                                    ApphudError("Registration: error"),
                                )
                            }
                        }
                    }
                } else {
                    mainScope.launch {
                        completionHandler?.invoke(currentUser, null)
                    }
                }
            }
        }
    }

    private suspend fun repeatRegistrationSilent() {
        val newUser = RequestManager.registrationSync(!didRegisterCustomerAtThisLaunch, is_new, true)

        newUser?.let {
            storage.lastRegistration = System.currentTimeMillis()
            mainScope.launch { notifyLoadingCompleted(it) }
        }
    }

    internal fun productsFetchCallback(callback: (List<ProductDetails>) -> Unit) {
        if (productDetails.isNotEmpty()) {
            callback.invoke(productDetails)
        } else {
            customProductsFetchedBlock = callback
        }
    }

    internal fun performWhenOfferingsPrepared(callback: () -> Unit) {
        if (didLoadOfferings) {
            callback.invoke()
        } else {
            offeringsPreparedCallbacks.add(callback)
        }
    }

    //endregion

    //region === User Properties ===
    internal fun setUserProperty(
        key: ApphudUserPropertyKey,
        value: Any?,
        setOnce: Boolean,
        increment: Boolean,
    ) {
        val typeString = getType(value)
        if (typeString == "unknown") {
            val type = value?.let { value::class.java.name } ?: "unknown"
            val message = "For key '${key.key}' invalid property type: '$type' for 'value'. Must be one of: [Int, Float, Double, Boolean, String or null]"
            ApphudLog.logE(message)
            return
        }
        if (increment && !(typeString == "integer" || typeString == "float")) {
            val type = value?.let { value::class.java.name } ?: "unknown"
            val message = "For key '${key.key}' invalid increment property type: '$type' for 'value'. Must be one of: [Int, Float or Double]"
            ApphudLog.logE(message)
            return
        }

        val property =
            ApphudUserProperty(
                key = key.key,
                value = value,
                increment = increment,
                setOnce = setOnce,
                type = typeString,
            )

        if (!storage.needSendProperty(property)) {
            return
        }

        synchronized(pendingUserProperties) {
            pendingUserProperties.run {
                remove(property.key)
                put(property.key, property)
            }
        }

        synchronized(pendingUserProperties) {
            pendingUserProperties.run {
                remove(property.key)
                put(property.key, property)
            }
        }
        setNeedsToUpdateUserProperties = true
    }

    private fun updateUserProperties() {
        setNeedsToUpdateUserProperties = false
        if (pendingUserProperties.isEmpty()) return

        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logE(it.message)
            } ?: run {
                val properties = mutableListOf<Map<String, Any?>>()
                val sentPropertiesForSave = mutableListOf<ApphudUserProperty>()

                synchronized(pendingUserProperties) {
                    pendingUserProperties.forEach {
                        properties.add(it.value.toJSON()!!)
                        if (!it.value.increment && it.value.value != null) {
                            sentPropertiesForSave.add(it.value)
                        }
                    }
                }

                val body = UserPropertiesBody(this.deviceId, properties)
                coroutineScope.launch(errorHandler) {
                    RequestManager.userProperties(body) { userProperties, error ->
                        mainScope.launch {
                            userProperties?.let {
                                if (userProperties.success) {
                                    val propertiesInStorage = storage.properties
                                    sentPropertiesForSave.forEach {
                                        propertiesInStorage?.put(it.key, it)
                                    }
                                    storage.properties = propertiesInStorage

                                    synchronized(pendingUserProperties) {
                                        pendingUserProperties.clear()
                                    }

                                    ApphudLog.logI("User Properties successfully updated.")
                                } else {
                                    val message = "User Properties update failed with errors"
                                    ApphudLog.logE(message)
                                }
                            }
                            error?.let {
                                ApphudLog.logE(message = it.message)
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun updateUserId(userId: UserId) {
        ApphudLog.log("Start updateUserId userId=$userId")

        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logE(it.message)
            } ?: run {
                val id = updateUser(id = userId)
                this.userId = id
                RequestManager.setParams(this.context, userId, this.deviceId, this.apiKey)

                coroutineScope.launch(errorHandler) {
                    val customer = RequestManager.registrationSync(!didRegisterCustomerAtThisLaunch, is_new, true)
                    customer?.let {
                        mainScope.launch {
                            notifyLoadingCompleted(it)
                        }
                    }
                    error?.let {
                        ApphudLog.logE(it.message)
                    }
                }
            }
        }
    }
    //endregion

    //region === Primary methods ===
    fun permissionGroups(): List<ApphudGroup> {
        var out: MutableList<ApphudGroup>
        synchronized(this.productGroups) {
            out = this.productGroups.toCollection(mutableListOf())
        }
        return out
    }

    fun grantPromotional(
        daysCount: Int,
        productId: String?,
        permissionGroup: ApphudGroup?,
        callback: ((Boolean) -> Unit)?,
    ) {
        performWhenUserRegistered { error ->
            error?.let {
                callback?.invoke(false)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    RequestManager.grantPromotional(daysCount, productId, permissionGroup) { customer, error ->
                        mainScope.launch {
                            customer?.let {
                                notifyLoadingCompleted(it)
                                callback?.invoke(true)
                                ApphudLog.logI("Promotional is granted")
                            } ?: run {
                                callback?.invoke(false)
                                ApphudLog.logI("Promotional is NOT granted")
                            }
                            error?.let {
                                callback?.invoke(false)
                                ApphudLog.logI("Promotional is NOT granted")
                            }
                        }
                    }
                }
            }
        }
    }

    fun subscriptions(): List<ApphudSubscription> {
        var subscriptions: MutableList<ApphudSubscription> = mutableListOf()
        this.currentUser?.let { user ->
            synchronized(user) {
                subscriptions = user.subscriptions.toCollection(mutableListOf())
            }
        }
        return subscriptions.filter { !it.isTemporary || it.isActive() }
    }

    fun purchases(): List<ApphudNonRenewingPurchase> {
        var purchases: MutableList<ApphudNonRenewingPurchase> = mutableListOf()
        this.currentUser?.let { user ->
            synchronized(user) {
                purchases = user.purchases.toCollection(mutableListOf())
            }
        }
        return purchases.filter { !it.isTemporary || it.isActive() }
    }

    fun paywallShown(paywall: ApphudPaywall) {
        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logI(error.message)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallShown(paywall)
                }
            }
        }
    }

    fun paywallClosed(paywall: ApphudPaywall) {
        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logI(error.message)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallClosed(paywall)
                }
            }
        }
    }

    internal fun paywallCheckoutInitiated(
        paywallId: String?,
        placementId: String?,
        productId: String?,
    ) {
        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logI(error.message)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallCheckoutInitiated(paywallId, placementId, productId)
                }
            }
        }
    }

    internal fun paywallPaymentCancelled(
        paywallId: String?,
        placementId: String?,
        productId: String?,
        errorCode: Int,
    ) {
        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logI(error.message)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    if (errorCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                        RequestManager.paywallPaymentCancelled(paywallId, placementId, productId)
                    } else {
                        val errorMessage = ApphudBillingResponseCodes.getName(errorCode)
                        RequestManager.paywallPaymentError(paywallId, placementId, productId, errorMessage)
                    }
                }
            }
        }
    }

    internal fun performWhenUserRegistered(callback: (ApphudError?) -> Unit) {
        if (!isInitialized()) {
            callback.invoke(ApphudError(MUST_REGISTER_ERROR))
            return
        }

        currentUser?.let {
            if (it.isTemporary == false) {
                callback.invoke(null)
            } else {
                registration(this.userId, this.deviceId) { _, error ->
                    callback.invoke(error)
                }
            }
        } ?: run {
            registration(this.userId, this.deviceId) { _, error ->
                callback.invoke(error)
            }
        }
    }

    internal fun getProductDetailsList(): List<ProductDetails> {
        var out: MutableList<ProductDetails>
        synchronized(this.productDetails) {
            out = this.productDetails.toCollection(mutableListOf())
        }
        return out
    }

    fun sendErrorLogs(message: String) {
        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logI(error.message)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    RequestManager.sendErrorLogs(message)
                }
            }
        }
    }

    private suspend fun fetchAdvertisingId(): String? {
        return RequestManager.fetchAdvertisingId()
    }

    private suspend fun fetchAppSetId(): String? =
        suspendCancellableCoroutine { continuation ->
            val client = AppSet.getClient(applicationContext)
            val task: Task<AppSetIdInfo> = client.appSetIdInfo
            task.addOnSuccessListener {
                // Determine current scope of app set ID.
                val scope: Int = it.scope

                // Read app set ID value, which uses version 4 of the
                // universally unique identifier (UUID) format.
                val id: String = it.id

                if (continuation.isActive) {
                    continuation.resume(id)
                }
            }
            task.addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
            task.addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private suspend fun fetchAndroidId(): String? =
        suspendCancellableCoroutine { continuation ->
            val androidId: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (continuation.isActive) {
                continuation.resume(androidId)
            }
        }

    @Synchronized
    fun collectDeviceIdentifiers() {
        if (!isInitialized()) {
            ApphudLog.logE("collectDeviceIdentifiers: $MUST_REGISTER_ERROR")
            return
        }

        if (ApphudUtils.optOutOfTracking) {
            ApphudLog.logE("Unable to collect device identifiers because optOutOfTracking() is called.")
            return
        }

        coroutineScope.launch(errorHandler) {
            var repeatRegistration = false
            var cachedIdentifiers = storage.deviceIdentifiers
            var newIdentifiers = arrayOf("", "", "")
            val threads =
                listOf(
                    async {
                        val adId = fetchAdvertisingId()
                        if (adId == null || adId == "00000000-0000-0000-0000-000000000000") {
                            ApphudLog.log("Unable to fetch Advertising ID, please check AD_ID permission in the manifest file.")
                        } else {
                            newIdentifiers[0] = adId
                        }
                    },
                    async {
                        val appSetID = fetchAppSetId()
                        appSetID?.let {
                            newIdentifiers[1] = it
                        }
                    },
                    async {
                        val androidID = fetchAndroidId()
                        androidID?.let {
                            newIdentifiers[2] = it
                        }
                    },
                )
            threads.awaitAll().let {
                if (!newIdentifiers.contentEquals(cachedIdentifiers)) {
                    storage.deviceIdentifiers = newIdentifiers
                    mutex.withLock {
                        repeatRegistrationSilent()
                    }
                } else {
                    ApphudLog.log("Device Identifiers not changed")
                }
            }
        }
    }

    //endregion//region === Secondary methods ===
    internal fun getPackageName(): String {
        return context.packageName
    }

    private fun isInitialized(): Boolean {
        return ::context.isInitialized &&
            ::userId.isInitialized &&
            ::deviceId.isInitialized &&
            ::apiKey.isInitialized
    }

    private fun getType(value: Any?): String {
        return when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "integer"
            is Float, is Double -> "float"
            null -> "null"
            else -> "unknown"
        }
    }

    internal fun logout() {
        clear()
    }

    private fun clear() {
        RequestManager.cleanRegistration()
        currentUser = null
        generatedUUID = UUID.randomUUID().toString()
        productsLoaded.set(0)
        customProductsFetchedBlock = null
        offeringsPreparedCallbacks.clear()
        storage.clean()
        prevPurchases.clear()
        productDetails.clear()
        pendingUserProperties.clear()
        allowIdentifyUser = true
        didRegisterCustomerAtThisLaunch = false
        setNeedsToUpdateUserProperties = false
    }

    private fun updateUser(id: UserId?): UserId {
        val userId =
            when {
                id.isNullOrBlank() -> {
                    storage.userId ?: generatedUUID
                }
                else -> {
                    id
                }
            }
        storage.userId = userId
        return userId
    }

    private fun updateDevice(id: DeviceId?): DeviceId {
        val deviceId =
            when {
                id.isNullOrBlank() -> {
                    storage.deviceId?.let {
                        is_new = false
                        it
                    } ?: generatedUUID
                }
                else -> {
                    id
                }
            }
        storage.deviceId = deviceId
        return deviceId
    }
    //endregion

    //region === Cache ===
    // Groups cache ======================================
    internal fun cacheGroups(groups: List<ApphudGroup>) {
        storage.productGroups = groups
    }

    private fun readGroupsFromCache(): MutableList<ApphudGroup> {
        return storage.productGroups?.toMutableList() ?: mutableListOf()
    }

    private fun updateGroupsWithProductDetails(productGroups: List<ApphudGroup>) {
        productGroups.forEach { group ->
            group.products?.forEach { product ->
                product.productDetails = getProductDetailsByProductId(product.productId)
            }
        }
    }

    // Paywalls cache ======================================
    internal fun cachePaywalls(paywalls: List<ApphudPaywall>) {
        storage.paywalls = paywalls
        Log.d("Apphud", "Did cache paywalls ${paywalls.count()}")
    }

    private fun readPaywallsFromCache(): List<ApphudPaywall> {
        Log.d("Apphud", "Will read paywalls from cache")
        return storage.paywalls ?: listOf()
    }

    private fun cachePlacements(placements: List<ApphudPlacement>) {
        storage.placements = placements
    }

    private fun readPlacementsFromCache(): List<ApphudPlacement> {
        return storage.placements ?: listOf()
    }

    private fun updatePaywallsAndPlacements() {
        synchronized(paywalls) {
            paywalls.forEach { paywall ->
                paywall.products?.forEach { product ->
                    product.paywallId = paywall.id
                    product.paywallIdentifier = paywall.identifier
                    product.productDetails = getProductDetailsByProductId(product.productId)
                }
            }
        }

        synchronized(placements) {
            placements.forEach { placement ->
                val paywall = placement.paywall
                paywall?.placementId = placement.id
                paywall?.products?.forEach { product ->
                    product.paywallId = placement.paywall.id
                    product.paywallIdentifier = placement.paywall.identifier
                    product.placementId = placement.id
                    product.placementIdentifier = placement.identifier
                    product.productDetails = getProductDetailsByProductId(product.productId)
                }
            }
        }
    }

    // Find ProductDetails  ======================================
    internal fun getProductDetailsByProductId(productIdentifier: String): ProductDetails? {
        var productDetail: ProductDetails?
        synchronized(productDetails) {
            productDetail = productDetails.let { productsList -> productsList.firstOrNull { it.productId == productIdentifier } }
        }
        return productDetail
    }
    //endregion
}
