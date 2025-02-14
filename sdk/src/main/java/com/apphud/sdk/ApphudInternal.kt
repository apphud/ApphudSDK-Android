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
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.body.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.LegacyHttpRetryInterceptor
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.managers.RequestManager.applicationContext
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.util.*
import kotlin.coroutines.resume
import kotlin.math.max

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {
    //region === Variables ===
    internal val mainScope = CoroutineScope(Dispatchers.Main)
    internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO )
    internal val errorHandler =
        CoroutineExceptionHandler { _, error ->
            error.message?.let { ApphudLog.logI("Coroutine exception: " + it) }
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
    internal var fromWeb2Web = false
    internal var hasRespondedToPaywallsRequest = false
    internal var refreshUserPending = false
    internal var sdkLaunchedAt: Long = System.currentTimeMillis()
    internal var offeringsCalledAt: Long = System.currentTimeMillis()
    internal var firstCustomerLoadedTime: Long? = null
    internal var productsLoadedTime: Long? = null
    internal var trackedAnalytics = false
    internal var observedOrders = mutableListOf <String>()
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
    internal var isUpdatingProperties = false

    private const val MUST_REGISTER_ERROR = " :You must call `Apphud.start` method before calling any other methods."
    internal var productGroups: MutableList<ApphudGroup> = mutableListOf()
    private var allowIdentifyUser = true
    internal var didRegisterCustomerAtThisLaunch = false
    private var isNew = true
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

    internal var purchaseCallbacks = mutableListOf<((ApphudPurchaseResult) -> Unit)>()
    internal var freshPurchase: Purchase? = null
        set(value) {
            field = value
            if (value != null) {
                scheduleLookupPurchase()
            } else {
                handler.removeCallbacks(lookupPurchaseRunnable)
            }
        }
    private val lookupPurchaseRunnable = Runnable { lookupFreshPurchase() }

    fun scheduleLookupPurchase(delay: Long = 7000L) {
        handler.removeCallbacks(lookupPurchaseRunnable)
        handler.postDelayed(lookupPurchaseRunnable, delay)
    }

    private var userRegisteredBlock: ((ApphudUser) -> Unit)? = null
    private var notifiedPaywallsAndPlacementsHandled = false
    internal var deferPlacements = false
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

                    if (storage.isNeedSync) {
                        // lookup immediately
                        lookupFreshPurchase(extraMessage = "recover_need_sync")
                    } else if (purchasingProduct != null && purchaseCallbacks.isNotEmpty()) {
                        scheduleLookupPurchase()
                    }
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

        this.context = context.applicationContext
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

        this.userRegisteredBlock = callback
        billing = BillingWrapper(this.context)
        RequestManager.setParams(this.context, this.apiKey)

        forceNotifyAllLoaded()

        val needRegistration = needRegistration(credentialsChanged, cachedPaywalls, cachedUser)

        ApphudLog.log("Need to register user: $needRegistration")

        if (needRegistration) {
            isRegisteringUser = true
            registration(this.userId, this.deviceId, true) { u, e ->
                if (shouldLoadProducts()) {
                    loadProducts()
                }
                coroutineScope.launch {
                    fetchNativePurchases()
                }
            }
        } else {
            mainScope.launch {
                notifyLoadingCompleted(cachedUser, null, true)
                if (shouldLoadProducts()) {
                    loadProducts()
                }
                coroutineScope.launch {
                    fetchNativePurchases()
                }
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

    internal fun refreshEntitlements(forceRefresh: Boolean = false, wasDeferred: Boolean = false, callback: ((ApphudUser?) -> Unit)? = null) {
        if (forceRefresh) {
            didRegisterCustomerAtThisLaunch = false
        }
        if (didRegisterCustomerAtThisLaunch || forceRefresh) {
            // do not call any offerings callbacks, because placements were deferred
            if (wasDeferred) {
                isRegisteringUser = true
            }
            ApphudLog.log("RefreshEntitlements: didRegister:$didRegisterCustomerAtThisLaunch force:$forceRefresh wasDeferred: $wasDeferred isDeferred: $deferPlacements")
            registration(this.userId, this.deviceId, true) { cust, _ ->
                if (wasDeferred) {
                    productsStatus = ApphudProductsStatus.none
                }
                loadProducts()
                callback?.invoke(cust)
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
            it.forEach { detail ->
                if (!productDetails.map { it.productId }.contains(detail.productId)) {
                    productDetails.add(detail)
                }
            }

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
            hasRespondedToPaywallsRequest = hasRespondedToPaywallsRequest || paywalls.isNotEmpty() || placements.isNotEmpty() || observerMode

            // TODO: should be called only if something changed
            coroutineScope.launch {
                delay(500)
                mainScope.launch {
                    currentUser?.let{ user ->
                        apphudListener?.apphudNonRenewingPurchasesUpdated(user.purchases)
                        apphudListener?.apphudSubscriptionsUpdated(user.subscriptions)
                    }
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
        } else if (!isRegisteringUser && (hasRespondedToPaywallsRequest || customerError != null) &&
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
//            ApphudLog.log("Not yet ready for callbacks invoke: isRegisteringUser: ${isRegisteringUser}, currentUserExist: ${currentUser != null} customerError: ${customerError}, latestCustomerError: ${latestCustomerLoadError}, paywallsEmpty: ${paywalls.isEmpty()}, productsResponseCode = ${productsResponseCode}, productsStatus: ${productsStatus}, productDetailsEmpty: ${productDetails.isEmpty()}, deferred: $deferPlacements, hasRespondedToPaywallsRequest=$hasRespondedToPaywallsRequest }")
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
            val shouldUnlockMutex = (currentUser == null || currentUser?.isTemporary == true || forceRegistration) && offeringsPreparedCallbacks.isNotEmpty() && !isRegisteringUser && mutex.isLocked
            if (shouldUnlockMutex) {
                try {
                    ApphudLog.log("Unlocking the mutex")
                    mutex.unlock()
                } catch (e: Exception) {
                    ApphudLog.log("Failed to unlock the mutex, force registration")
                    startRegistrationCall(userId, deviceId, forceRegistration, completionHandler)
                }
            }
            mutex.lock()
            try {
                if (currentUser == null || forceRegistration) {
                    startRegistrationCall(userId, deviceId, forceRegistration, completionHandler)
                } else {
                    mainScope.launch {
                        isRegisteringUser = false
                        completionHandler?.invoke(currentUser, null)
                    }
                }
            } finally {
                if (mutex.isLocked) {
                    mutex.unlock()
                }
            }
        }
    }

    private fun startRegistrationCall(userId: UserId,
                                      deviceId: DeviceId,
                                      forceRegistration: Boolean = false,
                                      completionHandler: ((ApphudUser?, ApphudError?) -> Unit)?) {

        val needPlacementsPaywalls = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode

        ApphudLog.log(
            "Registration conditions: user_is_null=${currentUser == null}, forceRegistration=$forceRegistration, isTemporary=${currentUser?.isTemporary}, requesting Placements = $needPlacementsPaywalls",
        )

        RequestManager.registrationLegacy(needPlacementsPaywalls, isNew, forceRegistration) { customer, error ->
            customer?.let {
                if (firstCustomerLoadedTime == null) {
                    firstCustomerLoadedTime = System.currentTimeMillis()
                }

                LegacyHttpRetryInterceptor.MAX_COUNT = APPHUD_DEFAULT_RETRIES

                currentUser = it
                if (it.paywalls.isNotEmpty()) {
                    synchronized(paywalls) {
                        paywalls = it.paywalls
                    }
                    synchronized(placements) {
                        placements = it.placements
                    }
                }

                coroutineScope.launch {
                    storage.lastRegistration = System.currentTimeMillis()
                }

                mainScope.launch {
                    // finish registering only here to avoid bug
                    isRegisteringUser = false
                    hasRespondedToPaywallsRequest = needPlacementsPaywalls
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
    }

    private suspend fun repeatRegistrationSilent() {
        val needPlacementsPaywalls = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode
        val newUser = RequestManager.registrationSync(needPlacementsPaywalls, isNew, true)

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

            if (observerMode) {
                observerMode = false
                ApphudLog.logE("Trying to access Placements or Paywalls while being in Observer Mode. This is a developer error. Disabling Observer Mode as a fallback...")
            }

            if (deferPlacements) {
                ApphudLog.log("Placements were deferred, force refresh them")
                offeringsPreparedCallbacks.add(callback)
                deferPlacements = false
                refreshEntitlements(true, wasDeferred = true)
            } else {
                val willRefresh = refreshPaywallsIfNeeded()
                val isWaitingForProducts = !finishedLoadingProducts()
                if ((isWaitingForProducts || willRefresh) && !fallbackMode) {
                    offeringsPreparedCallbacks.add(callback)
                    ApphudLog.log("Saved offerings callback")
                } else {
                    callback.invoke(null)
                }
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



    internal fun forceFlushUserProperties(force: Boolean, completion: ((Boolean) -> Unit)?) {
        setNeedsToUpdateUserProperties = false
        if (pendingUserProperties.isEmpty()) {
            completion?.invoke(false)
            return
        }

        if (isUpdatingProperties && !force) {
            return
        }
        isUpdatingProperties = true

        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logE("Failed to update user properties: " + it.message)
                isUpdatingProperties = false
                completion?.invoke(false)
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

                val body = UserPropertiesBody(this.deviceId, properties, force)
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
                                ApphudLog.logE("Failed to update user properties: " + it.message)
                            }
                            isUpdatingProperties = false
                            completion?.invoke(error == null)
                        }
                    }
                }
            }
        }
    }

    private fun updateUserProperties() {
        forceFlushUserProperties(false) { _ -> }
    }

    internal fun updateUserId(userId: UserId, email: String? = null, web2Web: Boolean? = false, callback: ((ApphudUser?) -> Unit)?) {
        if (userId.isBlank()) {
            ApphudLog.log("Invalid UserId=$userId")
            callback?.invoke(currentUser)
            return
        }
        ApphudLog.log("Start updateUserId userId=$userId")

        performWhenUserRegistered { error ->
            error?.let {
                ApphudLog.logE(it.message)
                callback?.invoke(currentUser)
            } ?: run {
                val originalUserId = this.userId
                if (web2Web == false) {
                    this.userId = userId
                    storage.userId = userId
                }
                RequestManager.setParams(this.context, this.apiKey)

                coroutineScope.launch(errorHandler) {
                    if (web2Web == true) {
                        ApphudInternal.userId = userId
                        ApphudInternal.fromWeb2Web = true
                    }
                    val needPlacementsPaywalls = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode
                    val customer = RequestManager.registrationSync(needPlacementsPaywalls, isNew, true, userId = userId, email)
                    ApphudInternal.userId = customer?.userId ?: currentUser?.userId ?: originalUserId
                    storage.userId = ApphudInternal.userId
                    customer?.let {
                        mainScope.launch {
                            notifyLoadingCompleted(it)
                            callback?.invoke(currentUser)
                        }
                    } ?: {
                        callback?.invoke(currentUser)
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

    fun getPlacements(): List<ApphudPlacement> {
        synchronized(placements) {
            return placements.toCollection(mutableListOf())
        }
    }

    fun getPermissionGroups(): List<ApphudGroup> {
        synchronized(productGroups) {
            return this.productGroups.toList()
        }
    }

    suspend fun loadPermissionGroups(): List<ApphudGroup> {

        synchronized(this.productGroups) {
            if (this.productGroups.isNotEmpty() && !storage.needUpdateProductGroups()) {
                return this.productGroups.toList()
            }
        }

        val groups = runCatchingCancellable { RequestManager.allProducts()}.getOrElse { emptyList() }

        if (groups.isNotEmpty()) {
            cacheGroups(groups)
        }

        synchronized(this.productGroups) {
            this.productGroups = groups.toMutableList()
        }

        coroutineScope.launch {
            fetchProducts()
            respondWithProducts()
        }

        return groups
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
                    repeatRegistrationSilent()
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
        purchaseCallbacks.clear()
        freshPurchase = null
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
