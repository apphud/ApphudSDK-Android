package com.apphud.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.body.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.managers.HttpRetryInterceptor
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
import kotlin.math.max
import kotlin.math.min

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {
    //region === Variables ===
    internal val mainScope = CoroutineScope(Dispatchers.Main)
    internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val errorHandler =
        CoroutineExceptionHandler { _, error ->
            error.message?.let { ApphudLog.logE("Coroutine exception: " + it) }
        }

    internal val FALLBACK_ERRORS = listOf(APPHUD_ERROR_TIMEOUT, 404, 500, 502, 503)
    internal var ignoreCache: Boolean = false
    internal lateinit var billing: BillingWrapper
    internal val storage by lazy { SharedPreferencesStorage.getInstance(context) }
    internal var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
    internal var productDetails = mutableListOf<ProductDetails>()
    internal var paywalls = listOf<ApphudPaywall>()
    internal var placements = listOf<ApphudPlacement>()
    internal var isRegisteringUser = false
    internal var refreshUserPending = false
    internal var sdkLaunchedAt: Long = System.currentTimeMillis()
    internal var offeringsCalledAt: Long = System.currentTimeMillis()
    internal var firstCustomerLoadedTime: Long? = null
    internal var productsLoadedTime: Long? = null
    internal var trackedAnalytics = false
    internal var latestCustomerLoadError: ApphudError? = null
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
    internal var userLoadRetryCount: Int = 1
    internal var notifiedAboutPaywallsDidFullyLoaded = false
    internal var purchasingProduct: ApphudProduct? = null
    internal var preferredTimeout: Double = 999_999.0
    private var customProductsFetchedBlock: ((List<ProductDetails>) -> Unit)? = null
    private var offeringsPreparedCallbacks = mutableListOf<((ApphudError?) -> Unit)?>()
    private var userRegisteredBlock: ((ApphudUser) -> Unit)? = null
    private var notifiedPaywallsAndPlacementsHandled = false
    internal var isActive = false
    internal var observerMode = false
    private var lifecycleEventObserver =
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (fallbackMode) {
                        storage.isNeedSync = true
                    }
                    isActive = false
                    ApphudLog.log("Application stopped [need sync ${storage.isNeedSync}]")
                }
                Lifecycle.Event.ON_START -> {
                    // do nothing
                    ApphudLog.log("Application resumed")
                    isActive = true
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
        inputUserId: UserId?,
        inputDeviceId: DeviceId?,
        observerMode: Boolean,
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
        allowIdentifyUser = false
        this.observerMode = observerMode

        mainScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }

        ApphudLog.log("Start initialization with userId=$inputUserId, deviceId=$inputDeviceId")
        if (apiKey.isEmpty()) throw Exception("ApiKey can't be empty")

        this.context = context
        this.apiKey = apiKey
        val isValid = storage.validateCaches()
        if (ignoreCache) {
            ApphudLog.logI("Ignoring local paywalls cache")
        }

        val cachedUser = if (isValid) storage.apphudUser else null
        val cachedPaywalls = if (ignoreCache || !isValid || observerMode) null else readPaywallsFromCache()
        val cachedPlacements = if (ignoreCache || !isValid || observerMode) null else readPlacementsFromCache()
        val cachedGroups = if (isValid) readGroupsFromCache() else mutableListOf()
        val cachedDeviceId = storage.deviceId
        val cachedUserId = storage.userId

        sdkLaunchedAt = System.currentTimeMillis()

        val generatedUUID = UUID.randomUUID().toString()

        val newUserId =
            if (inputUserId.isNullOrBlank()) {
                cachedUserId ?: generatedUUID
            } else {
                inputUserId
            }
        val newDeviceId =
            if (inputDeviceId.isNullOrBlank()) {
                cachedDeviceId ?: generatedUUID
            } else {
                inputDeviceId
            }

        val credentialsChanged = cachedUserId != newUserId || cachedDeviceId != newDeviceId

        if (credentialsChanged) {
            storage.userId = newUserId
            storage.deviceId = newDeviceId
        }

        /**
         * We cannot get paywalls and placements from paying current user,
         * because paying currentUser doesn't have cache timeout because it has
         * purchases history
         *
         * But paywalls and placements must have cache timeout
         */
        this.userId = newUserId
        this.deviceId = newDeviceId
        this.currentUser = cachedUser
        this.productGroups = cachedGroups
        cachedPaywalls?.let { this.paywalls = it }
        cachedPlacements?.let { this.placements = it }

        ApphudLog.log("Finished loading models")

        this.userRegisteredBlock = callback
        billing = BillingWrapper(context)
        RequestManager.setParams(this.context, this.apiKey)

        ApphudLog.log("Starting Requests")

        loadProducts()
        forceNotifyAllLoaded()

        val needRegistration = needRegistration(credentialsChanged, cachedPaywalls, cachedUser)

        if (needRegistration) {
            isRegisteringUser = true
            registration(this.userId, this.deviceId, true, null)
        } else {
            mainScope.launch {
                notifyLoadingCompleted(cachedUser, null, true)
            }
        }
    }

    //endregion

    //region === Registration ===
    private fun needRegistration(
        credentialsChanged: Boolean,
        cachedPaywalls: List<ApphudPaywall>?,
        cachedUser: ApphudUser?,
    ): Boolean {
        return credentialsChanged ||
            cachedPaywalls == null ||
            cachedUser == null ||
            cachedUser.hasPurchases() ||
            storage.cacheExpired(cachedUser)
    }

    internal fun refreshEntitlements(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            didRegisterCustomerAtThisLaunch = false
        }
        if (didRegisterCustomerAtThisLaunch || forceRefresh) {
            ApphudLog.log("RefreshEntitlements: didRegister:$didRegisterCustomerAtThisLaunch force:$forceRefresh")
            registration(this.userId, this.deviceId, true) { _, _ ->
                loadProducts()
            }
        }
    }

    @Synchronized
    internal fun notifyLoadingCompleted(
        customerLoaded: ApphudUser? = null,
        productDetailsLoaded: List<ProductDetails>? = null,
        fromCache: Boolean = false,
        fromFallback: Boolean = false,
        customerError: ApphudError? = null,
    ) {
        var paywallsPrepared = true

        customerError?.let {
            ApphudLog.logE("Customer Registration Error: ${it}")
            latestCustomerLoadError = it
        }

        if (latestCustomerLoadError == null && RequestManager.previousException != null) {
            latestCustomerLoadError = ApphudError.from(RequestManager.previousException!!)
            RequestManager.previousException = null
        }

        if (observerMode && (productDetails.isNotEmpty() || productDetailsLoaded != null) &&
            (firstCustomerLoadedTime != null || latestCustomerLoadError != null) &&
            !trackedAnalytics) {
            trackAnalytics(latestCustomerLoadError == null)
        }

        productDetailsLoaded?.let {
            productGroups = readGroupsFromCache()
            updateGroupsWithProductDetails(productGroups)

            synchronized(productDetails) {
                // notify that productDetails are loaded
                if (productDetails.isNotEmpty()) {
                    apphudListener?.apphudFetchProductDetails(productDetails)
                    customProductsFetchedBlock?.invoke(productDetails)
                }
            }
        }

        customerLoaded?.let {
            var updateOfferingsFromCustomer = false

            if (fromCache || fromFallback) {
            } else {
                if (it.paywalls.isNotEmpty()) {
                    updateOfferingsFromCustomer = true
                    coroutineScope.launch {
                        cachePaywalls(it.paywalls)
                        cachePlacements(it.placements)
                    }
                } else {
                    /* Attention:
                     * If customer loaded without paywalls, do not reload paywalls from cache!
                     * If cache time is over, paywall from cache will be NULL
                     */
                    paywallsPrepared = false
                }
                coroutineScope.launch {
                    val changed = storage.updateCustomer(it)
                    if (changed) {
                        mainScope.launch {
                            apphudListener?.apphudDidChangeUserID(it.userId)
                        }
                    }
                }
            }

            if (updateOfferingsFromCustomer) {
                paywalls = it.paywalls
                placements = it.placements
            } else if ((!ignoreCache && paywallsPrepared) || fromFallback || fallbackMode) {
                readPaywallsFromCache()?.let { cached -> paywalls = cached }
                readPlacementsFromCache()?.let { cached -> placements = cached }

                if (paywalls.isEmpty() && it.paywalls.isNotEmpty()) {
                    paywalls = it.paywalls
                    placements = it.placements
                }
            }

            currentUser = it
            userId = it.userId

            // TODO: should be called only if something changed
            coroutineScope.launch {
                delay(500)
                mainScope.launch {
                    apphudListener?.apphudNonRenewingPurchasesUpdated(currentUser!!.purchases)
                    apphudListener?.apphudSubscriptionsUpdated(currentUser!!.subscriptions)
                }
            }

            if (!didRegisterCustomerAtThisLaunch) {
                apphudListener?.userDidLoad(it)
                this.userRegisteredBlock?.invoke(it)
                this.userRegisteredBlock = null
                if (it.isTemporary == false && !fallbackMode) {
                    didRegisterCustomerAtThisLaunch = true
                }
            }

            if (it.isTemporary != true && fallbackMode && !fromCache) {
                disableFallback()
            }
        }

        updatePaywallsAndPlacements()
        handlePaywallsAndProductsLoaded(customerError)

        customerError?.let { handleCustomerError(it) }
    }

    private fun handlePaywallsAndProductsLoaded(customerError: ApphudError?) {

        if (currentUser != null && paywalls.isNotEmpty() && productDetails.isNotEmpty() && !isRegisteringUser) {
            if (!notifiedAboutPaywallsDidFullyLoaded) {
                apphudListener?.paywallsDidFullyLoad(paywalls)
                apphudListener?.placementsDidFullyLoad(placements)

                notifiedAboutPaywallsDidFullyLoaded = true
                ApphudLog.logI("Paywalls and Placements ready")
            }

            if (offeringsPreparedCallbacks.isNotEmpty()) {
                ApphudLog.log("handle offeringsPreparedCallbacks latestError: ${latestCustomerLoadError}")
            }
            while (offeringsPreparedCallbacks.isNotEmpty()) {
                val callback = offeringsPreparedCallbacks.removeFirst()
                callback?.invoke(null)
            }

            notifiedPaywallsAndPlacementsHandled = true
            trackAnalytics(true)

            latestCustomerLoadError = null
        } else if (!isRegisteringUser &&
            ((customerError != null && paywalls.isEmpty()) || (productsStatus != ApphudProductsStatus.loading && productsResponseCode != BillingClient.BillingResponseCode.OK && productDetails.isEmpty()))) {
            val error = latestCustomerLoadError ?: customerError ?: (if (productsResponseCode == APPHUD_NO_REQUEST) ApphudError("Paywalls load error", errorCode = productsResponseCode) else ApphudError("Google Billing error", errorCode = productsResponseCode))
            if (offeringsPreparedCallbacks.isNotEmpty()) {
                ApphudLog.log("handle offeringsPreparedCallbacks with error ${error}")
            }
            while (offeringsPreparedCallbacks.isNotEmpty()) {
                val callback = offeringsPreparedCallbacks.removeFirst()
                callback?.invoke(error)
            }

            notifiedPaywallsAndPlacementsHandled = true
            trackAnalytics(false)

            latestCustomerLoadError = null
        } else {
//            ApphudLog.log("Not yet ready for callbacks invoke: isRegisteringUser: ${isRegisteringUser}, currentUserExist: ${currentUser != null} customerError: ${customerError}, latestCustomerError: ${latestCustomerLoadError}, paywallsEmpty: ${paywalls.isEmpty()}, productsResponseCode = ${productsResponseCode}, productDetailsEmpty: ${productDetails.isEmpty()} }")
        }
    }

    private fun trackAnalytics(success: Boolean) {
        if (trackedAnalytics) { return }

        trackedAnalytics = true
        val totalLoad = (System.currentTimeMillis() - sdkLaunchedAt)
        val userLoad = if (firstCustomerLoadedTime != null) (firstCustomerLoadedTime!! - sdkLaunchedAt) else 0
        val productsLoaded = productsLoadedTime ?: 0
        ApphudLog.logI("SDK Benchmarks: User ${userLoad}ms, Products: ${productsLoaded}ms, Total: ${totalLoad}ms, Apphud Error: ${latestCustomerLoadError?.message}, Billing Response Code: ${productsResponseCode}, ErrorCode: ${latestCustomerLoadError?.errorCode}")
        coroutineScope.launch {
            RequestManager.sendPaywallLogs(
                sdkLaunchedAt,
                productDetails.count(),
                userLoad.toDouble(),
                productsLoaded.toDouble(),
                totalLoad.toDouble(),
                latestCustomerLoadError,
                productsResponseCode,
                success)
        }
    }

    private fun handleCustomerError(customerError: ApphudError) {
        if ((currentUser == null || productDetails.isEmpty() || (paywalls.isEmpty() && !observerMode)) && isActive && !refreshUserPending && userLoadRetryCount < APPHUD_INFINITE_RETRIES) {
            refreshUserPending = true
            coroutineScope.launch {
                val delay = 500L * userLoadRetryCount
                ApphudLog.logE("Customer Registration issue, will refresh in ${delay}ms")
                delay(delay)
                userLoadRetryCount += 1
                refreshPaywallsIfNeeded()
                refreshUserPending = false
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

                    RequestManager.registration(!didRegisterCustomerAtThisLaunch, is_new, forceRegistration) { customer, error ->
                        customer?.let {
                            if (firstCustomerLoadedTime == null) {
                                firstCustomerLoadedTime = System.currentTimeMillis()
                            }

                            HttpRetryInterceptor.MAX_COUNT = APPHUD_DEFAULT_RETRIES

                            currentUser = it
                            if (it.paywalls.isNotEmpty()) {
                                synchronized(paywalls) {
                                    paywalls = it.paywalls
                                }
                                synchronized(placements) {
                                    placements = it.placements
                                }
                            }
                            isRegisteringUser = false
                            coroutineScope.launch {
                                storage.lastRegistration = System.currentTimeMillis()
                            }

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
                                    fetchNativePurchases(forceRefresh = true)
                                }
                            }
                        } ?: run {
                            ApphudLog.logE("Registration failed ${error?.message}")
                            mainScope.launch {
                                isRegisteringUser = false
                                notifyLoadingCompleted(currentUser, null, true, currentUser?.isTemporary ?: false, error)
                                completionHandler?.invoke(currentUser, error)
                            }
                        }
                    }
                } else {
                    mainScope.launch {
                        isRegisteringUser = false
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

    internal fun forceNotifyAllLoaded() {
        coroutineScope.launch {
            if (preferredTimeout > 60) { return@launch }
            delay((preferredTimeout * 1000.0 * 1.5).toLong())
            mainScope.launch {
                if (!notifiedAboutPaywallsDidFullyLoaded || offeringsPreparedCallbacks.isNotEmpty()) {
                    ApphudLog.logE("Force Notify About Current State")
                    notifyLoadingCompleted(
                        currentUser,
                        productDetails,
                        false,
                        false,
                        latestCustomerLoadError
                    )
                }
            }
        }
    }

    internal fun shouldRetryRequest(request: String): Boolean {
        val diff = (System.currentTimeMillis() - max(offeringsCalledAt, sdkLaunchedAt))/1000.0

        // if paywalls callback not yet invoked and there are pending callbacks, and it's a customers request
        // and more than preferred timeout seconds lapsed then no time for extra retry.
        if (diff > preferredTimeout && offeringsPreparedCallbacks.isNotEmpty() && !notifiedAboutPaywallsDidFullyLoaded) {
            if (request.endsWith("products")) {
                ApphudLog.log("MAX TIMEOUT REACHED FOR $request")
                return false
            } else if (request.endsWith("customers") && !didRegisterCustomerAtThisLaunch) {
                ApphudLog.log("MAX TIMEOUT REACHED FOR $request")
                return false
            } else if (request.endsWith("billing")) {
                ApphudLog.log("MAX TIMEOUT REACHED FOR $request")
                return false
            }
        }

        return true
    }

    internal fun productsFetchCallback(callback: (List<ProductDetails>) -> Unit) {
        if (productDetails.isNotEmpty()) {
            callback.invoke(productDetails)
        } else {
            customProductsFetchedBlock = callback
        }
    }

    internal fun performWhenOfferingsPrepared(preferredTimeout: Double?, callback: (ApphudError?) -> Unit) {
        preferredTimeout?.let{
            this.preferredTimeout = max(it, APPHUD_DEFAULT_MAX_TIMEOUT)
            this.offeringsCalledAt = System.currentTimeMillis()
            currentPoductsLoadingCounts = 0
        }

        mainScope.launch {
            val willRefresh = refreshPaywallsIfNeeded()
            val isWaitingForProducts = !finishedLoadingProducts()
            if ((isWaitingForProducts || willRefresh) && !fallbackMode) {
                offeringsPreparedCallbacks.add(callback)
            } else {
                callback.invoke(null)
            }
        }
    }

    internal fun refreshPaywallsIfNeeded(): Boolean {
        var isLoading = false

        if (isRegisteringUser) {
            // already loading
            isLoading = true
        } else if (currentUser == null || fallbackMode || currentUser?.isTemporary == true || (paywalls.isEmpty() && !observerMode) || latestCustomerLoadError != null) {
            ApphudLog.logI("Refreshing User")
            didRegisterCustomerAtThisLaunch = false
            refreshEntitlements(true)
            isLoading = true
        }

        if (shouldLoadProducts()) {
            ApphudLog.logI("Refreshing Products")
            loadProducts()
            isLoading = true
        }

        if (!isLoading) {
//            ApphudLog.logI("No need to refresh")
        }

        return isLoading
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
        if (userId.isBlank()) {
            ApphudLog.log("Invalid UserId=$userId")
            return
        }
        ApphudLog.log("Start updateUserId userId=$userId")

        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logE(it.message)
            } ?: run {
                this.userId = userId
                storage.userId = userId
                RequestManager.setParams(this.context, this.apiKey)

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
                callback.invoke(ApphudError("Fallback mode"))
                refreshPaywallsIfNeeded()
            }
        } ?: run {
            registration(this.userId, this.deviceId) { _, error ->
                callback.invoke(error)
            }
        }
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
            val androidId: String? = fetchAndroidIdSync()
            if (continuation.isActive) {
                continuation.resume(androidId)
            }
        }

    fun fetchAndroidIdSync(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    fun getProductDetails(): List<ProductDetails> {
        synchronized(productDetails) {
            return productDetails.toCollection(mutableListOf())
        }
    }

    fun getPaywalls(): List<ApphudPaywall> {
        synchronized(paywalls) {
            return paywalls.toCollection(mutableListOf())
        }
    }

    fun getPermissionGroups(): List<ApphudGroup> {
        var out: MutableList<ApphudGroup>
        synchronized(this.productGroups) {
            out = this.productGroups.toCollection(mutableListOf())
        }
        return out
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
            val cachedIdentifiers = storage.deviceIdentifiers
            val newIdentifiers = arrayOf("", "", "")
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
        productsStatus = ApphudProductsStatus.none
        productsResponseCode = BillingClient.BillingResponseCode.OK
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
    }

    private fun readPaywallsFromCache(): List<ApphudPaywall>? {
        return storage.paywalls
    }

    private fun cachePlacements(placements: List<ApphudPlacement>) {
        storage.placements = placements
    }

    private fun readPlacementsFromCache(): List<ApphudPlacement>? {
        return storage.placements
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
                paywall?.placementIdentifier = placement.identifier
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
        var productDetail: ProductDetails? = null
        synchronized(productDetails){
            productDetail = productDetails.firstOrNull { it.productId == productIdentifier }
        }
        return productDetail
    }
    //endregion
}
