package com.apphud.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
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
import com.apphud.sdk.internal.data.ProductLoadingState
import com.apphud.sdk.internal.domain.model.ApiKey as ApiKeyModel
import com.apphud.sdk.internal.presentation.figma.FigmaWebViewActivity
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {
    //region === Variables ===
    internal var mainScope = CoroutineScope(Dispatchers.Main)
    internal var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val FALLBACK_ERRORS = listOf(APPHUD_ERROR_TIMEOUT, 404, 500, 502, 503)
    internal var ignoreCache: Boolean = false
    internal val billing: BillingWrapper
        get() = ServiceLocator.instance.billingWrapper
    internal val userRepository
        get() = ServiceLocator.instance.userRepository
    internal val productRepository
        get() = ServiceLocator.instance.productRepository
    internal val userPropertiesManager
        get() = ServiceLocator.instance.userPropertiesManager
    internal val analyticsTracker
        get() = ServiceLocator.instance.analyticsTracker
    internal val offeringsCallbackManager
        get() = ServiceLocator.instance.offeringsCallbackManager
    internal val storage: SharedPreferencesStorage
        get() = ServiceLocator.instance.storage
    internal val prevPurchases = CopyOnWriteArraySet<PurchaseRecordDetails>()
    internal val productDetails: List<ProductDetails>
        get() = productRepository.state.value.products

    internal var isRegisteringUser = false

    @Volatile
    internal var fromWeb2Web = false
    internal var hasRespondedToPaywallsRequest = false
    internal var refreshUserPending = false
    internal val observedOrders = ConcurrentHashMap.newKeySet<String>()
    private val handler: Handler = Handler(Looper.getMainLooper())

    private const val MUST_REGISTER_ERROR = " :You must call `Apphud.start` method before calling any other methods."
    internal val productGroups = AtomicReference<List<ApphudGroup>>(emptyList())

    @Volatile
    private var allowIdentifyUser = true
    internal var didRegisterCustomerAtThisLaunch = false
    private var isNew = true
    private lateinit var apiKey: ApiKey
    internal var fallbackMode = false
    internal lateinit var context: Context

    internal var apphudListener: ApphudListener? = null
    internal var userLoadRetryCount: Int = 1
    internal var purchasingProduct: ApphudProduct? = null
    internal var preferredTimeout: Double = 999_999.0

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

        val cachedPlacements =
            if (ignoreCache || !isValid || observerMode) null else userRepository.getCurrentUser()?.placements

        analyticsTracker.recordSdkLaunch()

        val credentialsChanged =
            ServiceLocator.instance.resolveCredentialsUseCase(inputUserId, inputDeviceId).credentialsChanged

        this.productGroups.set(if (isValid) storage.productGroups.orEmpty() else emptyList())

        this.userRegisteredBlock = callback
        RequestManager.setParams(this.context, this.apiKey)

        forceNotifyAllLoaded()

        val needRegistration = needRegistration(credentialsChanged, cachedPlacements)

        ApphudLog.log("Need to register user: $needRegistration")

        coroutineScope.launch {
            if (needRegistration) {
                runCatchingCancellable { registration() }
                    .onFailure { ApphudLog.logE("Registration failed in initialize: ${it.message}") }
            } else {
                mainScope.launch { notifyLoadingCompleted(customerLoaded = userRepository.getCurrentUser()) }
            }
            postInitSetup()
        }
    }

    private suspend fun postInitSetup() {
        if (shouldLoadProducts()) {
            loadProducts()
        }
        ServiceLocator.instance.fetchNativePurchasesUseCase()
        userRepository.getDeviceId()?.let { ServiceLocator.instance.ruleController.start(it) }
    }

    //endregion

    //region === Registration ===
    private fun needRegistration(
        credentialsChanged: Boolean,
        cachedPlacements: List<ApphudPlacement>?,
    ): Boolean {
        val cachedUser = userRepository.getCurrentUser()
        return credentialsChanged ||
            cachedPlacements == null ||
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
                productRepository.reset()
            }
            loadProducts()
        }.getOrNull()
    }

    @Synchronized
    private fun shouldTrackObserverAnalytics(productDetailsLoaded: List<ProductDetails>?): Boolean =
        observerMode &&
            (productDetails.isNotEmpty() || productDetailsLoaded != null) &&
            (analyticsTracker.isFirstCustomerLoaded || offeringsCallbackManager.getCustomerLoadError() != null) &&
            !analyticsTracker.trackedAnalytics

    internal fun notifyLoadingCompleted(
        customerLoaded: ApphudUser? = null,
        productDetailsLoaded: List<ProductDetails>? = null,
        fromFallback: Boolean = false,
        customerError: ApphudError? = null,
    ) {
        customerError?.let {
            ApphudLog.logE("Customer Registration Error: ${it}")
            offeringsCallbackManager.setCustomerLoadError(it)
        }

        if (offeringsCallbackManager.getCustomerLoadError() == null && RequestManager.previousException != null) {
            offeringsCallbackManager.setCustomerLoadError(ApphudError.from(originalCause = RequestManager.previousException!!))
            RequestManager.previousException = null
        }

        if (shouldTrackObserverAnalytics(productDetailsLoaded)) {
            val state = productRepository.state.value
            val responseCode =
                if (state is ProductLoadingState.Failed) state.responseCode else BillingClient.BillingResponseCode.OK
            analyticsTracker.trackAnalytics(
                offeringsCallbackManager.getCustomerLoadError() == null,
                offeringsCallbackManager.getCustomerLoadError(),
                productDetails.count(),
                responseCode
            )
        }

        productDetailsLoaded?.let {
            updateProductState(it)

            // notify that productDetails are loaded
            if (productDetails.isNotEmpty()) {
                apphudListener?.apphudFetchProductDetails(productDetails.toList())
                offeringsCallbackManager.notifyProductsFetched(productDetails.toList())
            }
        }

        customerLoaded?.let {
            updateUserState(it, fromFallback)

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
        offeringsCallbackManager.handlePaywallsAndProductsLoaded(
            customerError = customerError,
            isRegisteringUser = isRegisteringUser,
            productDetails = productDetails,
            hasRespondedToPaywallsRequest = hasRespondedToPaywallsRequest,
            deferPlacements = deferPlacements,
            apphudListener = apphudListener,
        )

        customerError?.let { handleCustomerError(it) }
    }

    private fun handleCustomerError(customerError: ApphudError) {
        val user = userRepository.getCurrentUser()
        if (customerError.isRetryable() && (user == null || productDetails.isEmpty() || ((user.placements.isEmpty()) && !observerMode)) && isActive && !refreshUserPending && userLoadRetryCount < APPHUD_INFINITE_RETRIES) {
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

            analyticsTracker.recordFirstCustomerLoaded()

            updateUserState(newUser)

            if (storage.isNeedSync) {
                coroutineScope.launch {
                    runCatchingCancellable {
                        ApphudLog.log("Registration: isNeedSync true, start syncing")
                        ServiceLocator.instance.fetchNativePurchasesUseCase()
                    }.onFailure { error ->
                        ApphudLog.logE("Error syncing native purchases: ${error.message}")
                    }
                }
            }

            isRegisteringUser = false
            hasRespondedToPaywallsRequest = needPlacementsPaywalls
            notifyLoadingCompleted(customerLoaded = newUser)

            userPropertiesManager.flushIfNeeded()

            newUser
        }.getOrElse { error ->
            ApphudLog.logE("Registration failed: ${error.message}")
            isRegisteringUser = false

            val cachedUser = userRepository.getCurrentUser()
            withContext(Dispatchers.Main) {
                notifyLoadingCompleted(
                    customerLoaded = cachedUser,
                    productDetailsLoaded = null,
                    fromFallback = cachedUser?.isTemporary ?: false,
                    customerError = error.toApphudError()
                )
            }

            throw error.toApphudError()
        }
    }

    internal fun forceNotifyAllLoaded() {
        coroutineScope.launch {
            if (preferredTimeout > 60) {
                return@launch
            }
            delay((preferredTimeout * 1000.0 * 1.5).toLong())
            mainScope.launch {
                if (offeringsCallbackManager.hasPendingWork()) {
                    ApphudLog.logE("Force Notify About Current State")
                    notifyLoadingCompleted(
                        customerLoaded = userRepository.getCurrentUser(),
                        productDetailsLoaded = productDetails,
                        customerError = offeringsCallbackManager.getCustomerLoadError()
                    )
                }
            }
        }
    }

    internal fun shouldRetryRequest(request: String): Boolean {
        return analyticsTracker.shouldRetryRequest(
            request = request,
            hasPendingCallbacks = offeringsCallbackManager.hasPendingCallbacks(),
            notifiedAboutPaywallsDidFullyLoaded = offeringsCallbackManager.isFullyLoaded(),
            didRegisterCustomerAtThisLaunch = didRegisterCustomerAtThisLaunch,
            preferredTimeout = preferredTimeout,
        )
    }

    internal fun performWhenOfferingsPrepared(preferredTimeout: Double?, callback: (ApphudError?) -> Unit) {
        preferredTimeout?.let {
            this.preferredTimeout = max(it, APPHUD_DEFAULT_MAX_TIMEOUT)
            this.analyticsTracker.recordOfferingsCalled()
            // Retry counts are now managed by state transitions
        }

        mainScope.launch {

            if (observerMode) {
                observerMode = false
                ApphudLog.logE("Trying to access Placements or Paywalls while being in Observer Mode. This is a developer error. Disabling Observer Mode as a fallback...")
            }

            if (deferPlacements) {
                ApphudLog.log("Placements were deferred, force refresh them")
                offeringsCallbackManager.addOfferingsCallback(callback)
                deferPlacements = false
                refreshEntitlements(true, wasDeferred = true)
            } else {
                val willRefresh = refreshPaywallsIfNeeded()
                val isWaitingForProducts = !finishedLoadingProducts()
                if ((isWaitingForProducts || willRefresh) && !fallbackMode) {
                    offeringsCallbackManager.addOfferingsCallback(callback)
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
        } else if (user == null || fallbackMode || user.isTemporary == true || ((user.placements.isEmpty()) && !observerMode) || offeringsCallbackManager.getCustomerLoadError() != null) {
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

        return isLoading
    }

//endregion

    //region === User Properties ===

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

        userRepository.setUserId(userId)
        RequestManager.setParams(this.context, this.apiKey)

        if (web2Web == true) {
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

        customer?.let {
            mainScope.launch { notifyLoadingCompleted(customerLoaded = it) }
        }
        return userRepository.getCurrentUser()
    }

//endregion

    //region === Primary methods ===
    fun paywallShown(paywall: ApphudPaywall) {
        coroutineScope.launch {
            runCatchingCancellable { paywallShownSuspend(paywall) }
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
        coroutineScope.launch {
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
        coroutineScope.launch {
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
                notifyLoadingCompleted(customerLoaded = user)
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

        coroutineScope.launch {
            runCatchingCancellable {
                fetchProducts()
                respondWithProducts()
            }.onFailure { error ->
                ApphudLog.logE("Error fetching products: ${error.message}")
            }
        }

        return groups
    }

    @Synchronized
    fun collectDeviceIdentifiers() {
        if (!isInitialized()) {
            ApphudLog.logE("collectDeviceIdentifiers: $MUST_REGISTER_ERROR")
            return
        }
        coroutineScope.launch {
            runCatchingCancellable {
                val needPP = !didRegisterCustomerAtThisLaunch && !deferPlacements && !observerMode
                val user = ServiceLocator.instance.deviceIdentifiersInteractor(
                    scope = this,
                    needPlacementsPaywalls = needPP,
                    isNew = isNew,
                )
                user?.let { mainScope.launch { notifyLoadingCompleted(customerLoaded = it) } }
            }.onFailure { ApphudLog.logE("Error collecting device identifiers: ${it.message}") }
        }
    }

    //endregion//region === Secondary methods ===
    internal fun getPackageName(): String {
        return context.packageName
    }

    private fun isInitialized(): Boolean {
        return ::context.isInitialized &&
            runCatching { userRepository.getUserId() }.getOrNull() != null &&
            runCatching { userRepository.getDeviceId() }.getOrNull() != null &&
            ::apiKey.isInitialized
    }

    internal fun logout() = synchronized(this) {
        clear()
    }

    private fun clear() {
        // Cancel all active coroutines to prevent race conditions when userId/deviceId become null
        coroutineScope.cancel()
        mainScope.cancel()

        // Recreate scopes for next initialization
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mainScope = CoroutineScope(Dispatchers.Main)

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

        // Reset products state to Idle
        runCatching {
            productRepository.reset()
        }.onFailure { e ->
            ApphudLog.log("ServiceLocator not initialized, skip productRepository.reset(): ${e.message}")
        }

        runCatching {
            ServiceLocator.instance.deviceIdentifiersRepository.clear()
        }.onFailure { e ->
            ApphudLog.log("ServiceLocator not initialized, skip deviceIdentifiersRepository.clear(): ${e.message}")
        }

        if (isInitialized()) {
            storage.clean()
        } else {
            ApphudLog.log("SDK not initialized, skip storage.clean()")
        }
        runCatching {
            offeringsCallbackManager.clear()
            userPropertiesManager.clear()
            analyticsTracker.reset()
        }.onFailure { e ->
            ApphudLog.log("ServiceLocator not initialized, skip managers clear: ${e.message}")
        }
        ServiceLocator.clearInstance()
        RequestManager.cleanRegistration()
        purchaseCallbacks.clear()
        freshPurchase = null
        prevPurchases.clear()
        productGroups.set(emptyList())
        allowIdentifyUser = true
        didRegisterCustomerAtThisLaunch = false
        ApphudLog.log("SDK did logout")
    }

//endregion

    //region === Cache ===
// Groups cache ======================================
    internal fun cacheGroups(groups: List<ApphudGroup>) {
        storage.productGroups = groups
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
        val userPlacements = user?.placements.orEmpty()

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
            val newUserId = user.userId
            mainScope.launch {
                apphudListener?.apphudDidChangeUserID(newUserId)
            }
        }

        hasRespondedToPaywallsRequest =
            hasRespondedToPaywallsRequest || user.placements.isNotEmpty() || observerMode

        // Disable fallback mode if needed
        if (user.isTemporary != true && fallbackMode) {
            disableFallback()
        }
    }

    private fun updateProductState(productsLoaded: List<ProductDetails>) {
        productGroups.set(storage.productGroups.orEmpty())
        updateGroupsWithProductDetails(productGroups.get())
    }
    //endregion
}
