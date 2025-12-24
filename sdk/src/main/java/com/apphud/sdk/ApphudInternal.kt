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
import com.apphud.sdk.body.UserPropertiesBody
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPaywallScreenShowResult
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.domain.PaywallEvent
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.domain.model.ApiKey as ApiKeyModel
import com.apphud.sdk.internal.presentation.figma.FigmaWebViewActivity
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.managers.RequestManager.applicationContext
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.max

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {
    //region === Variables ===
    internal val mainScope = CoroutineScope(Dispatchers.Main)
    internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val errorHandler =
        CoroutineExceptionHandler { _, error ->
            error.message?.let { ApphudLog.logI("Coroutine exception: " + it) }
        }

    internal val FALLBACK_ERRORS = listOf(APPHUD_ERROR_TIMEOUT, 404, 500, 502, 503)
    internal var ignoreCache: Boolean = false
    internal val billing: BillingWrapper
        get() = ServiceLocator.instance.billingWrapper
    internal val userRepository
        get() = ServiceLocator.instance.userRepository
    internal val storage: SharedPreferencesStorage
        get() = ServiceLocator.instance.storage
    internal val prevPurchases = CopyOnWriteArraySet<PurchaseRecordDetails>()
    internal val productDetails = CopyOnWriteArrayList<ProductDetails>()

    internal var isRegisteringUser = false

    @Volatile
    internal var fromWeb2Web = false
    internal var hasRespondedToPaywallsRequest = false
    internal var refreshUserPending = false
    internal var sdkLaunchedAt: Long = System.currentTimeMillis()
    internal var offeringsCalledAt: Long = System.currentTimeMillis()
    internal var firstCustomerLoadedTime: Long? = null
    internal var productsLoadedTime: Long? = null
    internal var trackedAnalytics = false
    internal val observedOrders = ConcurrentHashMap.newKeySet<String>()
    internal var latestCustomerLoadError: ApphudError? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val pendingUserProperties = ConcurrentHashMap<String, ApphudUserProperty>()

    @Volatile
    private var updateUserPropertiesJob: kotlinx.coroutines.Job? = null

    private var setNeedsToUpdateUserProperties: Boolean = false
        set(value) {
            field = value
            if (value) {
                updateUserPropertiesJob?.cancel()
                updateUserPropertiesJob = coroutineScope.launch(errorHandler) {
                    delay(1000L)
                    if (userRepository.getCurrentUser() != null) {
                        updateUserProperties()
                    } else {
                        setNeedsToUpdateUserProperties = true
                    }
                }
            } else {
                updateUserPropertiesJob?.cancel()
                updateUserPropertiesJob = null
            }
        }

    @Volatile
    internal var isUpdatingProperties = false

    private const val MUST_REGISTER_ERROR = " :You must call `Apphud.start` method before calling any other methods."
    internal val productGroups = AtomicReference<List<ApphudGroup>>(emptyList())

    @Volatile
    private var allowIdentifyUser = true
    internal var didRegisterCustomerAtThisLaunch = false
    private var isNew = true
    private lateinit var apiKey: ApiKey
    lateinit var deviceId: DeviceId
    internal var fallbackMode = false
    internal lateinit var userId: UserId
    internal lateinit var context: Context

    internal var apphudListener: ApphudListener? = null
    internal var userLoadRetryCount: Int = 1
    internal var notifiedAboutPaywallsDidFullyLoaded = false
    internal var purchasingProduct: ApphudProduct? = null
    internal var preferredTimeout: Double = 999_999.0
    private var customProductsFetchedBlock: ((List<ProductDetails>) -> Unit)? = null
    private val offeringsPreparedCallbacks = CopyOnWriteArrayList<((ApphudError?) -> Unit)?>()

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
        ruleCallback: ApphudRuleCallback,
    ) = synchronized(this) {
        if (!allowIdentifyUser) {
            ApphudLog.logE(
                " " +
                    "\n=============================================================" +
                    "\nAbort initializing, because Apphud SDK already initialized." +
                    "\nYou can only call `Apphud.start()` once per app lifecycle." +
                    "\nOr if `Apphud.logout()` was called previously." +
                    "\n=============================================================",
            )
            return@synchronized
        }
        allowIdentifyUser = false
        this.observerMode = observerMode

        this.context = context.applicationContext
        this.apiKey = apiKey

        ServiceLocator.ServiceLocatorInstanceFactory().create(
            applicationContext = context.applicationContext,
            ruleCallback = ruleCallback,
            apiKey = ApiKeyModel(apiKey)
        )

        mainScope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }

        ApphudLog.log("Start initialization with userId=$inputUserId, deviceId=$inputDeviceId")
        if (apiKey.isEmpty()) throw Exception("ApiKey can't be empty")

        val isValid = storage.validateCaches()
        if (ignoreCache) {
            ApphudLog.logI("Ignoring local paywalls cache")
        }

        val cachedPaywalls = if (ignoreCache || !isValid || observerMode) null else userRepository.getCurrentUser()?.paywalls
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

        this.userId = newUserId
        this.deviceId = newDeviceId

        this.productGroups.set(cachedGroups.toList())

        this.userRegisteredBlock = callback
        RequestManager.setParams(this.context, this.apiKey)

        forceNotifyAllLoaded()

        val needRegistration = needRegistration(credentialsChanged, cachedPaywalls)

        ApphudLog.log("Need to register user: $needRegistration")

        val ruleController = ServiceLocator.instance.ruleController
        if (needRegistration) {
            coroutineScope.launch {
                runCatchingCancellable {
                    registration()
                }.onSuccess {
                    if (shouldLoadProducts()) {
                        loadProducts()
                    }
                    ServiceLocator.instance.fetchNativePurchasesUseCase()
                    ruleController.start(deviceId)
                }.onFailure { error ->
                    ApphudLog.logE("Registration failed in initialize: ${error.message}")
                    // Even if registration failed, attempt to load products and start ruleController
                    if (shouldLoadProducts()) {
                        loadProducts()
                    }
                    ServiceLocator.instance.fetchNativePurchasesUseCase()
                    ruleController.start(deviceId)
                }
            }
        } else {
            mainScope.launch {
                notifyLoadingCompleted(userRepository.getCurrentUser(), null, true)
                if (shouldLoadProducts()) {
                    loadProducts()
                }
                coroutineScope.launch {
                    ServiceLocator.instance.fetchNativePurchasesUseCase()
                    ruleController.start(deviceId)
                }
            }
        }
    }

    //endregion

    //region === Registration ===
    private fun needRegistration(
        credentialsChanged: Boolean,
        cachedPaywalls: List<ApphudPaywall>?,
    ): Boolean {
        val cachedUser = userRepository.getCurrentUser()
        return credentialsChanged ||
            cachedPaywalls == null ||
            cachedUser == null ||
            cachedUser.isTemporary == true ||
            cachedUser.hasPurchases() ||
            storage.cacheExpired()
    }

    internal suspend fun refreshEntitlements(
        forceRefresh: Boolean = false,
        wasDeferred: Boolean = false,
    ): ApphudUser? {
        if (forceRefresh) {
            didRegisterCustomerAtThisLaunch = false
        }
        if (!didRegisterCustomerAtThisLaunch && !forceRefresh) {
            return null
        }

        if (wasDeferred) {
            isRegisteringUser = true
        }
        ApphudLog.log("RefreshEntitlements: didRegister:$didRegisterCustomerAtThisLaunch force:$forceRefresh wasDeferred: $wasDeferred isDeferred: $deferPlacements")

        return runCatchingCancellable {
            forceRegistration()
        }.onSuccess {
            if (wasDeferred) {
                productsStatus = ApphudProductsStatus.none
            }
            loadProducts()
        }.getOrNull()
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
            latestCustomerLoadError = ApphudError.from(originalCause = RequestManager.previousException!!)
            RequestManager.previousException = null
        }

        if (observerMode && (productDetails.isNotEmpty() || productDetailsLoaded != null) &&
            (firstCustomerLoadedTime != null || latestCustomerLoadError != null) &&
            !trackedAnalytics
        ) {
            trackAnalytics(latestCustomerLoadError == null)
        }

        productDetailsLoaded?.let {
            updateProductState(it)

            // notify that productDetails are loaded
            if (productDetails.isNotEmpty()) {
                apphudListener?.apphudFetchProductDetails(productDetails.toList())
                customProductsFetchedBlock?.invoke(productDetails.toList())
            }
        }

        customerLoaded?.let {
            updateUserState(it, fromFallback)

            if (!fromCache && !fromFallback && it.paywalls.isEmpty()) {
                /* Attention:
                 * If customer loaded without paywalls, do not reload paywalls from cache!
                 * If cache time is over, paywall from cache will be NULL
                 */
                paywallsPrepared = false
            }

            // TODO: should be called only if something changed
            coroutineScope.launch {
                delay(500)
                mainScope.launch {
                    userRepository.getCurrentUser()?.let { user ->
                        apphudListener?.apphudNonRenewingPurchasesUpdated(user.purchases.toList())
                        apphudListener?.apphudSubscriptionsUpdated(user.subscriptions.toList())
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

            // disableFallback() is now called in updateUserState
        }

        updatePaywallsAndPlacements()
        handlePaywallsAndProductsLoaded(customerError)

        customerError?.let { handleCustomerError(it) }
    }

    private fun handlePaywallsAndProductsLoaded(customerError: ApphudError?) {
        when {
            isDataReady() -> handleSuccessfulLoad()
            isErrorOccurred(customerError) -> handleError(customerError)
            else -> logNotReadyState()
        }
    }

    private fun isDataReady(): Boolean {
        val user = userRepository.getCurrentUser()
        return user != null &&
            user.paywalls.isNotEmpty() &&
            productDetails.isNotEmpty() &&
            !isRegisteringUser
    }

    private fun isErrorOccurred(customerError: ApphudError?): Boolean =
        !isRegisteringUser &&
            hasResponseOrError(customerError) &&
            hasDataLoadFailed(customerError)

    private fun hasResponseOrError(customerError: ApphudError?) =
        hasRespondedToPaywallsRequest || customerError != null

    private fun hasDataLoadFailed(customerError: ApphudError?) =
        (customerError != null && (userRepository.getCurrentUser()?.paywalls?.isEmpty() != false)) || isProductsLoadFailed()

    private fun isProductsLoadFailed() =
        productsStatus != ApphudProductsStatus.loading &&
            productsResponseCode != BillingClient.BillingResponseCode.OK &&
            productDetails.isEmpty()

    private fun handleSuccessfulLoad() {
        val user = userRepository.getCurrentUser()
        if (!notifiedAboutPaywallsDidFullyLoaded) {
            apphudListener?.paywallsDidFullyLoad(user?.paywalls?.toList().orEmpty())
            apphudListener?.placementsDidFullyLoad(user?.placements?.toList().orEmpty())

            notifiedAboutPaywallsDidFullyLoaded = true
            ApphudLog.logI("Paywalls and Placements ready")
        }

        if (offeringsPreparedCallbacks.isNotEmpty()) {
            ApphudLog.log("handle offeringsPreparedCallbacks latestError: $latestCustomerLoadError")
        }
        while (offeringsPreparedCallbacks.isNotEmpty()) {
            val callback = offeringsPreparedCallbacks.removeFirst()
            callback?.invoke(null)
        }

        notifiedPaywallsAndPlacementsHandled = true
        trackAnalytics(true)

        latestCustomerLoadError = null
    }

    private fun handleError(customerError: ApphudError?) {
        val error = latestCustomerLoadError ?: customerError ?: if (productsResponseCode == APPHUD_NO_REQUEST) {
            ApphudError("Paywalls load error", errorCode = productsResponseCode)
        } else {
            ApphudError("Google Billing error", errorCode = productsResponseCode)
        }

        if (offeringsPreparedCallbacks.isNotEmpty()) {
            ApphudLog.log("handle offeringsPreparedCallbacks with error $error")
        }
        while (offeringsPreparedCallbacks.isNotEmpty()) {
            val callback = offeringsPreparedCallbacks.removeFirst()
            callback?.invoke(error)
        }

        notifiedPaywallsAndPlacementsHandled = true
        trackAnalytics(false)

        latestCustomerLoadError = null
    }

    private fun logNotReadyState() {
        val user = userRepository.getCurrentUser()
        ApphudLog.log("Not yet ready for callbacks invoke: isRegisteringUser: $isRegisteringUser, currentUserExist: ${user != null}, latestCustomerError: $latestCustomerLoadError, paywallsEmpty: ${user?.paywalls?.isEmpty() != false}, productsResponseCode = $productsResponseCode, productsStatus: $productsStatus, productDetailsEmpty: ${productDetails.isEmpty()}, deferred: $deferPlacements, hasRespondedToPaywallsRequest=$hasRespondedToPaywallsRequest")
    }

    private fun trackAnalytics(success: Boolean) {
        if (trackedAnalytics) {
            return
        }

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
                success
            )
        }
    }

    private fun handleCustomerError(customerError: ApphudError) {
        val user = userRepository.getCurrentUser()
        if (customerError.isRetryable() && (user == null || productDetails.isEmpty() || ((user.paywalls.isEmpty()) && !observerMode)) && isActive && !refreshUserPending && userLoadRetryCount < APPHUD_INFINITE_RETRIES) {
            refreshUserPending = true
            coroutineScope.launch {
                val delay = 500L * userLoadRetryCount
                ApphudLog.logE("Customer Registration issue ${customerError.errorCode}, will refresh in ${delay}ms")
                delay(delay)
                userLoadRetryCount += 1
                refreshPaywallsIfNeeded()
                refreshUserPending = false
            }
        }
    }

    /**
     * Normal user registration
     * Uses cache if user already loaded
     *
     * @throws ApphudError if registration fails
     */
    private suspend fun registration(): ApphudUser {
        return performRegistration(forceRegistration = false)
    }

    /**
     * Force user registration
     * Always performs server request, ignoring cache
     *
     * @param userId optional userId for user switching
     * @param email optional email for update
     * @throws ApphudError if registration fails
     */
    private suspend fun forceRegistration(
        userId: String? = null,
        email: String? = null,
    ): ApphudUser {
        return performRegistration(forceRegistration = true, userId = userId, email = email)
    }

    /**
     * Internal method for performing registration via UseCase
     */
    private suspend fun performRegistration(
        forceRegistration: Boolean,
        userId: String? = null,
        email: String? = null,
    ): ApphudUser {
        isRegisteringUser = true

        val needPlacementsPaywalls = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode

        return runCatchingCancellable {
            val newUser = ServiceLocator.instance.registrationUseCase(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = forceRegistration,
                userId = userId,
                email = email
            )

            if (firstCustomerLoadedTime == null) {
                firstCustomerLoadedTime = System.currentTimeMillis()
            }

            updateUserState(newUser)

            if (storage.isNeedSync) {
                coroutineScope.launch(errorHandler) {
                    ApphudLog.log("Registration: isNeedSync true, start syncing")
                    ServiceLocator.instance.fetchNativePurchasesUseCase()
                }
            }

            isRegisteringUser = false
            hasRespondedToPaywallsRequest = needPlacementsPaywalls
            notifyLoadingCompleted(newUser)

            coroutineScope.launch {
                if (pendingUserProperties.isNotEmpty() && setNeedsToUpdateUserProperties) {
                    updateUserProperties()
                }
            }

            newUser
        }.getOrElse { error ->
            ApphudLog.logE("Registration failed: ${error.message}")
            isRegisteringUser = false

            val cachedUser = userRepository.getCurrentUser()
            withContext(Dispatchers.Main) {
                notifyLoadingCompleted(
                    customerLoaded = cachedUser,
                    productDetailsLoaded = null,
                    fromCache = true,
                    fromFallback = cachedUser?.isTemporary ?: false,
                    customerError = error.toApphudError()
                )
            }

            throw error.toApphudError()
        }
    }

    private suspend fun repeatRegistrationSilent() {
        val needPlacementsPaywalls = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode
        runCatchingCancellable {
            ServiceLocator.instance.registrationUseCase(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = true
            )
        }.onSuccess {
            mainScope.launch { notifyLoadingCompleted(it) }
        }
    }

    internal fun forceNotifyAllLoaded() {
        coroutineScope.launch {
            if (preferredTimeout > 60) {
                return@launch
            }
            delay((preferredTimeout * 1000.0 * 1.5).toLong())
            mainScope.launch {
                if (!notifiedAboutPaywallsDidFullyLoaded || offeringsPreparedCallbacks.isNotEmpty()) {
                    ApphudLog.logE("Force Notify About Current State")
                    notifyLoadingCompleted(
                        userRepository.getCurrentUser(),
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
        val diff = (System.currentTimeMillis() - max(offeringsCalledAt, sdkLaunchedAt)) / 1000.0

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
            callback.invoke(productDetails.toList())
        } else {
            customProductsFetchedBlock = callback
        }
    }

    internal fun performWhenOfferingsPrepared(preferredTimeout: Double?, callback: (ApphudError?) -> Unit) {
        preferredTimeout?.let {
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

    internal suspend fun refreshPaywallsIfNeeded(): Boolean {
        var isLoading = false
        val user = userRepository.getCurrentUser()

        if (isRegisteringUser) {
            // already loading
            isLoading = true
        } else if (user == null || fallbackMode || user.isTemporary == true || ((user.paywalls.isEmpty()) && !observerMode) || latestCustomerLoadError != null) {
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
            val message =
                "For key '${key.key}' invalid property type: '$type' for 'value'. Must be one of: [Int, Float, Double, Boolean, String or null]"
            ApphudLog.logE(message)
            return
        }
        if (increment && !(typeString == "integer" || typeString == "float")) {
            val type = value?.let { value::class.java.name } ?: "unknown"
            val message =
                "For key '${key.key}' invalid increment property type: '$type' for 'value'. Must be one of: [Int, Float or Double]"
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

        pendingUserProperties[property.key] = property
        setNeedsToUpdateUserProperties = true
    }

    internal suspend fun forceFlushUserProperties(force: Boolean): Boolean {
        setNeedsToUpdateUserProperties = false

        if (pendingUserProperties.isEmpty()) {
            return false
        }

        if (isUpdatingProperties && !force) {
            return false
        }
        isUpdatingProperties = true

        try {
            runCatchingCancellable { awaitUserRegistration() }
                .onFailure { error ->
                    ApphudLog.logE("Failed to update user properties: ${error.message}")
                    return false
                }

            val properties = mutableListOf<Map<String, Any?>>()
            val sentPropertiesForSave = mutableListOf<ApphudUserProperty>()

            pendingUserProperties.forEach {
                properties.add(it.value.toJSON()!!)
                if (!it.value.increment && it.value.value != null) {
                    sentPropertiesForSave.add(it.value)
                }
            }

            val body = UserPropertiesBody(deviceId, properties, force)

            return withContext(Dispatchers.IO) {
                runCatchingCancellable { RequestManager.postUserProperties(body) }
                    .fold(
                        onSuccess = { userProperties ->
                            if (userProperties.success) {
                                val propertiesInStorage = storage.properties
                                sentPropertiesForSave.forEach {
                                    propertiesInStorage?.put(it.key, it)
                                }
                                storage.properties = propertiesInStorage

                                pendingUserProperties.clear()

                                ApphudLog.logI("User Properties successfully updated.")
                                true
                            } else {
                                ApphudLog.logE("User Properties update failed with errors")
                                false
                            }
                        },
                        onFailure = {
                            ApphudLog.logE("Failed to update user properties: ${it.message}")
                            false
                        }
                    )
            }
        } finally {
            isUpdatingProperties = false
        }
    }


    private suspend fun updateUserProperties() {
        forceFlushUserProperties(false)
    }

    internal suspend fun updateUserId(
        userId: UserId,
        email: String? = null,
        web2Web: Boolean? = false,
    ): ApphudUser? {
        if (userId.isBlank()) {
            ApphudLog.log("Invalid UserId=$userId")
            return userRepository.getCurrentUser()
        }
        ApphudLog.log("Start updateUserId userId=$userId")

        runCatchingCancellable { awaitUserRegistration() }
            .onFailure { error ->
                ApphudLog.logE(error.message.orEmpty())
                return userRepository.getCurrentUser()
            }

        val originalUserId = this.userId
        if (web2Web == false) {
            this.userId = userId
            storage.userId = userId
        }
        RequestManager.setParams(this.context, this.apiKey)

        if (web2Web == true) {
            ApphudInternal.userId = userId
            fromWeb2Web = true
        }
        val needPlacementsPaywalls = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode
        val customer: ApphudUser? = runCatchingCancellable {
            ServiceLocator.instance.registrationUseCase(
                needPlacementsPaywalls = needPlacementsPaywalls,
                isNew = isNew,
                forceRegistration = true,
                userId = userId,
                email = email
            )
        }.getOrElse { error ->
            val apphudError = if (error is ApphudError) error else ApphudError.from(originalCause = error)
            ApphudLog.logE("updateUserId error: ${apphudError.message}")
            null
        }

        ApphudInternal.userId = customer?.userId ?: userRepository.getCurrentUser()?.userId ?: originalUserId
        storage.userId = ApphudInternal.userId

        customer?.let {
            mainScope.launch { notifyLoadingCompleted(it) }
        }
        return userRepository.getCurrentUser()
    }

//endregion

    //region === Primary methods ===
    fun paywallShown(paywall: ApphudPaywall) {
        coroutineScope.launch(errorHandler) {
            runCatchingCancellable { paywallShownSuspend(paywall) }
                .onFailure { error -> ApphudLog.logI(error.message ?: "Unknown error") }
        }
    }

    fun paywallClosed(paywall: ApphudPaywall) {
        coroutineScope.launch(errorHandler) {
            runCatchingCancellable { paywallClosedSuspend(paywall) }
                .onFailure { error -> ApphudLog.logI(error.message ?: "Unknown error") }
        }
    }


    suspend fun showPaywallScreen(
        context: Context,
        paywall: ApphudPaywall,
        callbacks: Apphud.ApphudPaywallScreenCallbacks,
        maxTimeout: Long,
    ) {
        ApphudLog.logI("Starting to show paywall screen for paywall: ${paywall.identifier}")

        coroutineScope {
            val eventsJob = launch {
                try {
                    ServiceLocator.instance.paywallEventManager.events.collect { event ->
                        withContext(Dispatchers.Main) {
                            when (event) {
                                is PaywallEvent.ScreenShown -> {
                                    ApphudLog.logI("[ApphudInternal] Paywall screen shown via event bus")
                                    callbacks.onScreenShown()
                                }
                                is PaywallEvent.TransactionStarted -> {
                                    ApphudLog.logI("[ApphudInternal] Transaction started via event bus: product=${event.product?.productId}")
                                    callbacks.onTransactionStarted(event.product)
                                }
                                is PaywallEvent.TransactionCompleted -> {
                                    ApphudLog.logI("[ApphudInternal] Transaction completed via event bus")
                                    callbacks.onTransactionCompleted(event.result)
                                    if (event.result !is ApphudPaywallScreenShowResult.TransactionError) {
                                        ApphudLog.logI("[ApphudInternal] Transaction completed successfully, terminating event stream")
                                        throw CancellationException("Transaction completed successfully")
                                    }
                                }
                                is PaywallEvent.CloseButtonTapped -> {
                                    ApphudLog.logI("[ApphudInternal] Close button tapped via event bus")
                                    callbacks.onCloseButtonTapped()
                                    ApphudLog.logI("[ApphudInternal] Paywall closed, terminating event stream")
                                    throw CancellationException("Paywall closed")
                                }
                                is PaywallEvent.ScreenError -> {
                                    ApphudLog.logE("[ApphudInternal] Screen error via event bus: ${event.error.message}")
                                    callbacks.onScreenError(event.error)
                                    ApphudLog.logI("[ApphudInternal] Screen error occurred, terminating event stream")
                                    throw CancellationException("Screen error occurred")
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    ApphudLog.logI("[ApphudInternal] Event subscription cancelled: ${e.message}")
                    throw e
                } catch (e: Exception) {
                    val error = e.toApphudError()
                    ApphudLog.logE("[ApphudInternal] Error in event subscription: ${error.message}")
                    withContext(Dispatchers.Main) {
                        callbacks.onScreenError(error)
                    }
                }
            }

            try {
                withTimeout(maxTimeout) {
                    val renderResult = ServiceLocator.instance.renderPaywallPropertiesUseCase(paywall).getOrThrow()

                    val renderItemsJson = ServiceLocator.instance.renderResultMapperWithSerializer.toJson(renderResult)
                    ApphudLog.logI("Serialized render items: $renderItemsJson")

                    val intent = FigmaWebViewActivity.getIntent(context, paywall.id, renderItemsJson)
                    context.startActivity(intent)

                    ApphudLog.logI("Paywall screen shown successfully for paywall: ${paywall.identifier}")
                }

                eventsJob.join()
            } catch (e: TimeoutCancellationException) {
                eventsJob.cancel()
                val error = e.toApphudError()
                ApphudLog.logE("[ApphudInternal] Timeout showing paywall screen: ${error.message}")
                withContext(Dispatchers.Main) {
                    callbacks.onScreenError(error)
                }
                throw e
            } catch (e: CancellationException) {
                eventsJob.cancel()
                throw e
            } catch (e: Throwable) {
                eventsJob.cancel()
                val error = e.toApphudError()
                ApphudLog.logE("[ApphudInternal] Error showing paywall screen: ${error.message}")
                withContext(Dispatchers.Main) {
                    callbacks.onScreenError(error)
                }
            }
        }
    }

    internal fun paywallCheckoutInitiated(
        paywallId: String?,
        placementId: String?,
        productId: String?,
        screenId: String?,
    ) {
        coroutineScope.launch(errorHandler) {
            runCatchingCancellable {
                paywallCheckoutInitiatedSuspend(paywallId, placementId, productId, screenId)
            }.onFailure { error -> ApphudLog.logI(error.message ?: "Unknown error") }
        }
    }

    internal fun paywallPaymentCancelled(
        paywallId: String?,
        placementId: String?,
        productId: String?,
        errorCode: Int,
    ) {
        coroutineScope.launch(errorHandler) {
            runCatchingCancellable {
                paywallPaymentCancelledSuspend(paywallId, placementId, productId, errorCode)
            }.onFailure { error -> ApphudLog.logI(error.message ?: "Unknown error") }
        }
    }

    // Suspend helper methods for event tracking
    internal suspend fun grantPromotionalSuspend(
        daysCount: Int,
        productId: String?,
        permissionGroup: ApphudGroup?,
    ): Boolean {
        awaitUserRegistration()

        return RequestManager.grantPromotional(daysCount, productId, permissionGroup)
            .onSuccess { user ->
                notifyLoadingCompleted(user)
                ApphudLog.logI("Promotional is granted")
            }
            .onFailure {
                ApphudLog.logI("Promotional is NOT granted")
            }
            .isSuccess
    }

    private suspend fun paywallShownSuspend(paywall: ApphudPaywall) {
        awaitUserRegistration()
        RequestManager.paywallShown(paywall)
    }

    private suspend fun paywallClosedSuspend(paywall: ApphudPaywall) {
        awaitUserRegistration()
        RequestManager.paywallClosed(paywall)
    }

    private suspend fun paywallCheckoutInitiatedSuspend(
        paywallId: String?,
        placementId: String?,
        productId: String?,
        screenId: String?,
    ) {
        awaitUserRegistration()
        RequestManager.paywallCheckoutInitiated(paywallId, placementId, productId, screenId)
    }

    private suspend fun paywallPaymentCancelledSuspend(
        paywallId: String?,
        placementId: String?,
        productId: String?,
        errorCode: Int,
    ) {
        awaitUserRegistration()
        if (errorCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            RequestManager.paywallPaymentCancelled(paywallId, placementId, productId)
        } else {
            val errorMessage = ApphudBillingResponseCodes.getName(errorCode)
            RequestManager.paywallPaymentError(paywallId, placementId, productId, errorMessage)
        }
    }

    internal suspend fun awaitUserRegistration() {
        if (!isInitialized()) {
            throw ApphudError(MUST_REGISTER_ERROR)
        }

        val mCurrentUser = userRepository.getCurrentUser()
        when {
            mCurrentUser == null -> {
                registration()
            }
            mCurrentUser.isTemporary != false -> {
                refreshPaywallsIfNeeded()
                throw ApphudError("Fallback mode")
            }
            else -> Unit
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

    fun getProductDetails(): List<ProductDetails> =
        productDetails.toList()

    fun getPermissionGroups(): List<ApphudGroup> {
        return this.productGroups.get()
    }

    suspend fun loadPermissionGroups(): List<ApphudGroup> {

        val currentGroups = this.productGroups.get()
        if (currentGroups.isNotEmpty() && !storage.needUpdateProductGroups()) {
            return currentGroups
        }

        val groups = runCatchingCancellable { RequestManager.allProducts() }.getOrElse { emptyList() }

        if (groups.isNotEmpty()) {
            cacheGroups(groups)
        }

        this.productGroups.set(groups.toList())

        coroutineScope.launch(errorHandler) {
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

    internal fun logout() = synchronized(this) {
        clear()
    }

    private fun clear() {
        runCatching {
            val controller = ServiceLocator.instance.ruleController
            controller.stop()
        }.onFailure { e ->
            ApphudLog.log("ServiceLocator not initialized, skip ruleController.stop(): ${e.message}")
        }

        // Clear user through Repository
        runCatching {
            userRepository.clearUser()
        }.onFailure { e ->
            ApphudLog.log("ServiceLocator not initialized, skip userRepository.clearUser(): ${e.message}")
        }

        ServiceLocator.clearInstance()
        RequestManager.cleanRegistration()
        productsStatus = ApphudProductsStatus.none
        productsResponseCode = BillingClient.BillingResponseCode.OK
        customProductsFetchedBlock = null
        offeringsPreparedCallbacks.clear()
        purchaseCallbacks.clear()
        freshPurchase = null
        if (isInitialized()) {
            storage.clean()
        } else {
            ApphudLog.log("SDK not initialized, skip storage.clean()")
        }
        prevPurchases.clear()
        productDetails.clear()
        productGroups.set(emptyList())
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

    private fun updatePaywallsAndPlacements() {
        val user = userRepository.getCurrentUser()
        val userPaywalls = user?.paywalls.orEmpty()
        val userPlacements = user?.placements.orEmpty()

        userPaywalls.forEach { paywall ->
            paywall.products?.forEach { product ->
                product.paywallId = paywall.id
                product.paywallIdentifier = paywall.identifier
                product.productDetails = getProductDetailsByProductId(product.productId)
            }
        }

        userPlacements.forEach { placement ->
            val paywall = placement.paywall ?: return@forEach
            paywall.placementId = placement.id
            paywall.placementIdentifier = placement.identifier
            paywall.products?.forEach { product ->
                product.paywallId = paywall.id
                product.paywallIdentifier = paywall.identifier
                product.placementId = placement.id
                product.placementIdentifier = placement.identifier
                product.productDetails = getProductDetailsByProductId(product.productId)
            }
        }
    }

    // Find ProductDetails  ======================================
    internal fun getProductDetailsByProductId(productIdentifier: String): ProductDetails? {
        return productDetails.firstOrNull { it.productId == productIdentifier }
    }
//endregion

    //region === State Update Methods ===
    /**
     * Update user state through Repository
     * Separation of concerns: state mutation is separated from notifications
     */
    private fun updateUserState(user: ApphudUser, fromFallback: Boolean = false) {
        val userIdChanged = userRepository.setCurrentUser(user)
        if (userIdChanged && !fromFallback) {
            mainScope.launch {
                apphudListener?.apphudDidChangeUserID(user.userId)
            }
        }

        userId = user.userId

        hasRespondedToPaywallsRequest =
            hasRespondedToPaywallsRequest || user.paywalls.isNotEmpty() || user.placements.isNotEmpty() || observerMode

        // Disable fallback mode if needed
        if (user.isTemporary != true && fallbackMode) {
            disableFallback()
        }
    }

    private fun updateProductState(productsLoaded: List<ProductDetails>) {
        synchronized(productDetails) {
            productsLoaded.forEach { detail ->
                if (!productDetails.map { it.productId }.contains(detail.productId)) {
                    productDetails.add(detail)
                }
            }
        }
        val cachedProductGroups = readGroupsFromCache()
        productGroups.set(cachedProductGroups.toList())
        updateGroupsWithProductDetails(productGroups.get())
    }
    //endregion
}
