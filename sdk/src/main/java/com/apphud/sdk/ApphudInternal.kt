package com.apphud.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.body.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.ApphudSkuDetailsCallback
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {

    private val builder = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()//need this to pass nullable values to JSON and from JSON
        .create()
    private val parser: Parser = GsonParser(builder)

    /**
     * @handler use for work with UI-thread. Save to storage, call callbacks
     */
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val billing by lazy { BillingWrapper(context) }
    private val storage by lazy { SharedPreferencesStorage(context, parser) }
    private var generatedUUID = UUID.randomUUID().toString()
    private var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
    private var tempPrevPurchases = mutableSetOf<PurchaseRecordDetails>()
    private var productsForRestore = mutableListOf<PurchaseHistoryRecord>()
    internal var paywalls: MutableList<ApphudPaywall> = mutableListOf()
    internal var productGroups: MutableList<ApphudGroup> = mutableListOf()

    private var allowIdentifyUser = true
    private var isRegistered = false
    private var didRetrievePaywallsAtThisLaunch = false

    internal lateinit var userId: UserId
    private lateinit var deviceId: DeviceId

    private var is_new = true

    private lateinit var apiKey: ApiKey
    private lateinit var context: Context

    internal val currentUser: Customer?
        get() = storage.customer
    internal var apphudListener: ApphudListener? = null

    private val skuDetails = mutableListOf<SkuDetails>()

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val errorHandler = CoroutineExceptionHandler { context, error ->
        error.message?.let { ApphudLog.logE(it) }
    }

    /**
     * 0 - we at start point without any skuDetails
     * 1 - we have only one loaded SkuType SUBS or INAPP
     * 2 - we have both loaded SkuType SUBS and INAPP
     * */
    private var skuDetailsIsLoaded: AtomicInteger = AtomicInteger(0)
    private var skuDetailsForFetchIsLoaded: AtomicInteger = AtomicInteger(0)
    private var skuDetailsForRestoreIsLoaded: AtomicInteger = AtomicInteger(0)
    private var purchasesForRestoreIsLoaded: AtomicInteger = AtomicInteger(0)

    private var customProductsFetchedBlock: ((List<SkuDetails>) -> Unit)? = null

    private var fetchPaywallsDelayedCallback: (() -> Unit)? = null

    private val pendingUserProperties = mutableMapOf<String, ApphudUserProperty>()
    private val userPropertiesRunnable = Runnable {
        if (isRegistered) {
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

    private var paywallsDelayedCallback: PaywallCallback? = null

    private val paywallsRunnable = Runnable {
        tryInvokePaywallsDelayedCallback()
    }

    private var setNeedsToUpdatePaywalls: Boolean = false
        set(value) {
            field = value
            if (value) {
                handler.removeCallbacks(paywallsRunnable)
                handler.postDelayed(paywallsRunnable, 200L)
            } else {
                handler.removeCallbacks(paywallsRunnable)
            }
        }

    internal fun updateUserId(userId: UserId) {
        ApphudLog.log("Start updateUserId userId=$userId")
        val id = updateUser(id = userId)
        this.userId = id

        coroutineScope.launch(errorHandler) {
            RequestManager.registration(!didRetrievePaywallsAtThisLaunch, is_new) { customer, error ->
                launch(Dispatchers.Main) {
                    customer?.let {
                        storage.customer = customer
                        ApphudLog.logI("End updateUserId customer=$customer")
                    }
                    error?.let{
                        ApphudLog.logE(it.message)
                    }
                }
            }
        }
    }

    internal fun initialize(
        context: Context,
        apiKey: ApiKey,
        userId: UserId?,
        deviceId: DeviceId?
    ) {
        if (!allowIdentifyUser) {
            ApphudLog.logE("=============================================================" +
                    "\nAbort initializing, because Apphud SDK already initialized." +
                    "\nYou can only call `Apphud.start()` once per app lifecycle." +
                    "\nOr if `Apphud.logout()` was called previously." +
                    "\n=============================================================")
            return
        }
        this.apiKey = apiKey
        this.context = context
        ApphudLog.log("Start initialize with userId=$userId, deviceId=$deviceId")
        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)

        RequestManager.setParams(this.context, this.userId, this.deviceId, this.apiKey)

        allowIdentifyUser = false
        ApphudLog.log("try restore cachedPaywalls")
        this.paywalls = cachedPaywalls()
        // try to continue anyway, because maybe already has cached data, try to fetch play market products
        fetchProducts()

        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")
        registration(this.userId, this.deviceId)
    }

    private fun fetchProducts() {
        billing.skuCallback = { details ->
            ApphudLog.log("fetchProducts: details from Google Billing: $details")
            skuDetailsIsLoaded.incrementAndGet()
            if (details.isNotEmpty()) {
                skuDetails.addAll(details)
            }
            if (skuDetailsIsLoaded.isBothLoaded()) {
                paywalls = cachedPaywalls()
                productGroups = cachedGroups()
                customProductsFetchedBlock?.invoke(skuDetails)
                apphudListener?.apphudFetchSkuDetailsProducts(skuDetails)
            }
        }

        coroutineScope.launch(errorHandler) {
            RequestManager.allProducts { groupsList, error ->
                launch(Dispatchers.Main) {
                    groupsList?.let { groups ->
                        ApphudLog.logI("fetchProducts: products from Apphud server: $groups")
                        cacheGroups(groups)
                        val ids = groups.map { it -> it.products?.map { it.product_id }!! }.flatten()
                        billing.details(BillingClient.SkuType.SUBS, ids)
                        billing.details(BillingClient.SkuType.INAPP, ids)
                    }
                    error?.let{
                        ApphudLog.logE(it.message)
                    }
                }
            }
        }
    }

    private fun registration(
        userId: UserId,
        deviceId: DeviceId
    ) {
        ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")

        coroutineScope.launch(errorHandler) {
            RequestManager.registration(!didRetrievePaywallsAtThisLaunch, is_new) { customer, error ->
                launch(Dispatchers.Main) {
                    customer?.let {
                        ApphudLog.logI("registration: registrationUser customer=$customer")
                        storage.customer = customer
                        apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                        apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)

                        if (customer.paywalls.isNotEmpty()) {
                            didRetrievePaywallsAtThisLaunch = true
                            processLoadedPaywalls(customer.paywalls, true)
                        }

                        // try to resend purchases, if prev requests was fail
                        if (storage.isNeedSync) {
                            ApphudLog.log("registration: syncPurchases")
                            syncPurchases()
                        }

                        if (pendingUserProperties.isNotEmpty() && setNeedsToUpdateUserProperties) {
                            ApphudLog.log("registration: we should update UserProperties")
                            updateUserProperties()
                        }
                    }
                    error?.let {
                        ApphudLog.logE(it.message)
                    }

                    if (fetchPaywallsDelayedCallback != null) {
                        fetchPaywallsDelayedCallback?.invoke()
                        fetchPaywallsDelayedCallback = null
                    }
                }
            }
        }

        ApphudLog.logI("End registration")
    }

    internal fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit) {
        customProductsFetchedBlock = callback
        if (skuDetails.isNotEmpty()) {
            customProductsFetchedBlock?.invoke(skuDetails)
        }
    }

    /**
     * This is main purchase fun
     * At start we should fill only **ONE** of this parameters: **productId** or **skuDetails** or **product**
     * */
    internal fun purchase(
        activity: Activity,
        productId: String?,
        skuDetails: SkuDetails?,
        product: ApphudProduct?,
        withValidation: Boolean = true,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        if (!productId.isNullOrEmpty()) {
            //if we have productId
            val sku = getSkuDetailsByProductId(productId)
            if (sku != null) {
                purchaseInternal(activity, sku, null, withValidation, callback)
            } else {
                fetchDetails(activity, productId, null, withValidation, callback)
            }
        } else if (skuDetails != null) {
            //if we have SkuDetails
            purchaseInternal(activity, skuDetails, null, withValidation, callback)
        } else {
            //if we have ApphudProduct
            product?.skuDetails?.let {
                purchaseInternal(activity, null, product, withValidation, callback)
            } ?: run {
                val sku = getSkuDetailsByProductId(product?.product_id!!)
                if (sku != null) {
                    purchaseInternal(activity, sku, null, withValidation, callback)
                } else {
                    fetchDetails(activity, null, product, withValidation, callback)
                }
            }
        }
    }

    private fun fetchDetails(
        activity: Activity,
        productId: String?,
        apphudProduct: ApphudProduct?,
        withValidation: Boolean,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        skuDetailsForFetchIsLoaded.set(0)
        val productName: String = productId ?: apphudProduct?.product_id!!
        ApphudLog.log("Could not find SkuDetails for product id: $productName in memory")
        ApphudLog.log("Now try fetch it from Google Billing")
        val fetchDetailsCallback: ApphudSkuDetailsCallback = { skuList ->
            skuDetailsForFetchIsLoaded.incrementAndGet()
            if (skuList.isNotEmpty()) {
                skuDetails.addAll(skuList)
                ApphudLog.log("Google Billing return this info for product id = $productName :")
                skuList.forEach { ApphudLog.log("$it") }
            }
            if (skuDetailsForFetchIsLoaded.isBothLoaded()) {
                //if we have successfully fetched SkuDetails with target productName
                getSkuDetailsByProductId(productName)?.let { sku ->
                    //if we have not empty ApphudProduct
                    apphudProduct?.let {
                        paywalls = cachedPaywalls()
                        it.skuDetails = sku
                        purchaseInternal(activity, null, it, withValidation, callback)
                    } ?: run {
                        purchaseInternal(activity, sku, null, withValidation, callback)
                    }
                } ?: run {
                    //if we booth SkuType already loaded and we still haven't any SkuDetails
                    val message = "Unable to fetch product with given product id: $productName" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                    ApphudLog.log(message = message,
                        sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null,
                        null,
                        null,
                        ApphudError(message)))
                }
            }
        }
        billing.details(type = BillingClient.SkuType.SUBS,
            products = listOf(productName),
            manualCallback = fetchDetailsCallback)
        billing.details(type = BillingClient.SkuType.INAPP,
            products = listOf(productName),
            manualCallback = fetchDetailsCallback)
    }

    private fun purchaseInternal(
        activity: Activity,
        details: SkuDetails?,
        apphudProduct: ApphudProduct?,
        withValidation: Boolean,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        billing.acknowledgeCallback = { status, purchase ->
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Failed to acknowledge purchase with code: ${status.error}" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                    ApphudLog.log(message = message,
                        sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null,
                        null,
                        purchase,
                        ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully acknowledged")
                    when {
                        withValidation -> ackPurchase(purchase, details, apphudProduct, callback)
                        else -> {
                            callback?.invoke(ApphudPurchaseResult(null, null, purchase, null))
                            ackPurchase(purchase, details, apphudProduct, null)
                        }
                    }
                }
            }
        }
        billing.consumeCallback = { status, purchase ->
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Failed to consume purchase with error: ${status.error}" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                    ApphudLog.log(message = message,
                        sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null,
                        null,
                        purchase,
                        ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully consumed: ${status.message}")
                    when {
                        withValidation -> ackPurchase(purchase, details, apphudProduct, callback)
                        else -> {
                            callback?.invoke(ApphudPurchaseResult(null, null, purchase, null))
                            ackPurchase(purchase, details, apphudProduct, null)
                        }
                    }
                }
            }
        }
        billing.purchasesCallback = { purchasesResult ->
            when (purchasesResult) {
                is PurchaseUpdatedCallbackStatus.Error -> {
                    var message = if (details != null) {
                        "Unable to buy product with given product id: ${details.sku} "
                    } else {
                        if (purchasesResult.result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                            paywallPaymentCancelled(apphudProduct?.paywall_id, apphudProduct?.product_id)
                        }
                        "Unable to buy product with given product id: ${apphudProduct?.skuDetails?.sku} "
                    }
                    apphudProduct?.let{
                        message += " [Apphud product ID: " + it.id + "]"
                    }

                    val error =
                        ApphudError(message = message,
                            secondErrorMessage = purchasesResult.result.debugMessage,
                            errorCode = purchasesResult.result.responseCode
                        )
                    ApphudLog.log(message = error.toString())

                    callback?.invoke(ApphudPurchaseResult(null, null, null, error))
                }
                is PurchaseUpdatedCallbackStatus.Success -> {
                    ApphudLog.log("purchases: $purchasesResult")
                    val detailsType = if (details != null) {
                        details.type
                    } else {
                        apphudProduct?.skuDetails?.type
                    }
                    purchasesResult.purchases.forEach {
                        when (it.purchaseState) {
                            Purchase.PurchaseState.PURCHASED ->
                                when (detailsType) {
                                    BillingClient.SkuType.SUBS -> {
                                        if (!it.isAcknowledged) {
                                            billing.acknowledge(it)
                                        }
                                    }
                                    BillingClient.SkuType.INAPP -> {
                                        billing.consume(it)
                                    }
                                    else -> {
                                        val message = "After purchase type is null"
                                        ApphudLog.log(message)
                                        callback?.invoke(ApphudPurchaseResult(null,
                                            null,
                                            it,
                                            ApphudError(message)))
                                    }
                                }
                            else -> {
                                val message = "After purchase state: ${it.purchaseState}" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                                ApphudLog.log(message = message)

                                callback?.invoke(ApphudPurchaseResult(null,
                                    null,
                                    it,
                                    ApphudError(message)))
                            }
                        }
                    }
                }
            }
        }
        when {
            details != null -> {
                billing.purchase(activity, details)
            }
            apphudProduct?.skuDetails != null -> {
                paywallCheckoutInitiated(apphudProduct.paywall_id, apphudProduct.product_id)
                billing.purchase(activity, apphudProduct.skuDetails!!)
            }
            else -> {
                val message = "Unable to buy product with because SkuDetails is null" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                ApphudLog.log(message = message)
                callback?.invoke(ApphudPurchaseResult(null,
                    null,
                    null,
                    ApphudError(message)))
            }
        }
    }

    private fun ackPurchase(
        purchase: Purchase,
        details: SkuDetails?,
        apphudProduct: ApphudProduct?,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        coroutineScope.launch(errorHandler) {
            RequestManager.purchased(purchase, details, apphudProduct) { customer, error ->
                launch(Dispatchers.Main) {
                    customer?.let {
                        val newSubscriptions =
                            customer.subscriptions.firstOrNull { it.productId == purchase.skus.first() }

                        val newPurchases =
                            customer.purchases.firstOrNull { it.productId == purchase.skus.first() }

                        storage.customer = customer
                        storage.isNeedSync = false

                        if (newSubscriptions == null && newPurchases == null) {
                            val productId = details?.let { details.sku } ?: purchase.skus.first()?:"unknown"
                            val message =
                                "Error! There are no new subscriptions " +
                                        "or new purchases from the Apphud server " +
                                        "after the purchase of $productId"
                            ApphudLog.logE(message)
                            callback?.invoke(ApphudPurchaseResult(null,
                                null,
                                null,
                                ApphudError(message)))
                        } else {
                            apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                            callback?.invoke(ApphudPurchaseResult(newSubscriptions,
                                newPurchases,
                                purchase,
                                null))
                        }
                    }
                    error?.let {
                        val message = "Unable to validate purchase with error = ${it.message}" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                        ApphudLog.logI(message = message)
                        callback?.invoke(ApphudPurchaseResult(null, null,
                            purchase,
                            ApphudError(message))
                        )
                    }
                }
            }
        }
    }

    internal fun restorePurchases(callback: ApphudPurchasesRestoreCallback) {
        syncPurchases(allowsReceiptRefresh = true, callback = callback)
    }

    internal fun syncPurchases(
        allowsReceiptRefresh: Boolean = false,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        storage.isNeedSync = true
        productsForRestore.clear()
        tempPrevPurchases.clear()
        purchasesForRestoreIsLoaded.set(0)
        skuDetailsForRestoreIsLoaded.set(0)
        billing.restoreCallback = { restoreStatus ->
            skuDetailsForRestoreIsLoaded.incrementAndGet()
            when (restoreStatus) {
                is PurchaseRestoredCallbackStatus.Error -> {
                    ApphudLog.log("SyncPurchases: restore purchases is failed coz ${restoreStatus.message}")
                    if (skuDetailsForRestoreIsLoaded.isBothLoaded()) {
                        if (tempPrevPurchases.isEmpty()) {
                            val error =
                                ApphudError(message = "Restore Purchases is failed for SkuType.SUBS and SkuType.INAPP",
                                    secondErrorMessage = restoreStatus.message,
                                    errorCode = restoreStatus.result?.responseCode)
                            ApphudLog.log(message = error.toString(), sendLogToServer = true)
                            callback?.invoke(null, null, error)
                        } else {
                            syncPurchasesWithApphud(tempPrevPurchases, callback)
                        }
                    }
                }
                is PurchaseRestoredCallbackStatus.Success -> {
                    ApphudLog.log("SyncPurchases: purchases was restored: ${restoreStatus.purchases}")
                    tempPrevPurchases.addAll(restoreStatus.purchases)

                    if (skuDetailsForRestoreIsLoaded.isBothLoaded()) {
                        if (!allowsReceiptRefresh && prevPurchases.containsAll(tempPrevPurchases)) {
                            ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                        } else {
                            syncPurchasesWithApphud(tempPrevPurchases, callback)
                        }
                    }
                }
            }
        }
        billing.historyCallback = { purchasesHistoryStatus ->
            purchasesForRestoreIsLoaded.incrementAndGet()
            when (purchasesHistoryStatus) {
                is PurchaseHistoryCallbackStatus.Error -> {
                    if (purchasesForRestoreIsLoaded.isBothLoaded()) {
                        val message =
                            "Restore Purchase History is failed for SkuType.SUBS and SkuType.INAPP " +
                                    "with message = ${purchasesHistoryStatus.result?.debugMessage}" +
                                    " and code = ${purchasesHistoryStatus.result?.responseCode}"
                        processPurchasesHistoryResults(message, callback)
                    }
                }
                is PurchaseHistoryCallbackStatus.Success -> {
                    if (!purchasesHistoryStatus.purchases.isNullOrEmpty())
                        productsForRestore.addAll(purchasesHistoryStatus.purchases)

                    if (purchasesForRestoreIsLoaded.isBothLoaded()) {
                        val message =
                            "Restore Purchase History is failed for SkuType.SUBS and SkuType.INAPP "
                        processPurchasesHistoryResults(message, callback)
                    }
                }
            }
        }
        billing.queryPurchaseHistory(BillingClient.SkuType.SUBS)
        billing.queryPurchaseHistory(BillingClient.SkuType.INAPP)
    }

    private fun processPurchasesHistoryResults(
        message: String,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        if (productsForRestore.isNullOrEmpty()) {
            ApphudLog.log(message = message, sendLogToServer = true)
            callback?.invoke(null, null, ApphudError(message = message))
        } else {
            ApphudLog.log("historyCallback: $productsForRestore")
            billing.restore(BillingClient.SkuType.SUBS, productsForRestore)
            billing.restore(BillingClient.SkuType.INAPP, productsForRestore)
        }
    }

    private fun syncPurchasesWithApphud(
        tempPurchaseRecordDetails: Set<PurchaseRecordDetails>,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        coroutineScope.launch(errorHandler) {
            RequestManager.restorePurchases(tempPurchaseRecordDetails) { customer, error ->
                launch(Dispatchers.Main) {
                    customer?.let {
                        prevPurchases.addAll(tempPurchaseRecordDetails)
                        storage.isNeedSync = false
                        storage.customer = customer
                        ApphudLog.logI("SyncPurchases: customer was updated $customer")
                        apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                        apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                        callback?.invoke(customer.subscriptions, customer.purchases, null)
                    }
                    error?.let {
                        val message = "Sync Purchases with Apphud is failed with message = ${error.message} and code = ${error.errorCode}"
                        ApphudLog.logE(message = message)
                        callback?.invoke(null, null, error)
                    }
                }
                ApphudLog.log("SyncPurchases: success send history purchases ${tempPurchaseRecordDetails.toList()}")
            }
        }
    }

    internal fun addAttribution(
        provider: ApphudAttributionProvider,
        data: Map<String, Any>? = null,
        identifier: String? = null
    ) {
        val body = when (provider) {
            ApphudAttributionProvider.adjust -> AttributionBody(
                device_id = deviceId,
                adid = identifier,
                adjust_data = data ?: emptyMap()
            )
            ApphudAttributionProvider.facebook -> {
                val map = mutableMapOf<String, Any>("fb_device" to true)
                    .also { map -> data?.let { map.putAll(it) } }
                    .toMap()
                AttributionBody(
                    device_id = deviceId,
                    facebook_data = map
                )
            }
            ApphudAttributionProvider.appsFlyer -> when (identifier) {
                null -> null
                else -> AttributionBody(
                    device_id = deviceId,
                    appsflyer_id = identifier,
                    appsflyer_data = data
                )
            }
            ApphudAttributionProvider.firebase -> when (identifier) {
                null -> null
                else -> AttributionBody(
                    device_id = deviceId,
                    firebase_id = identifier
                )
            }
        }

        when (provider) {
            ApphudAttributionProvider.appsFlyer -> {
                val temporary = storage.appsflyer
                when {
                    temporary == null -> Unit
                    temporary.id == body?.appsflyer_id -> return
                    temporary.data == body?.appsflyer_data -> return
                }
            }
            ApphudAttributionProvider.facebook -> {
                val temporary = storage.facebook
                when {
                    temporary == null -> Unit
                    temporary.data == body?.facebook_data -> return
                }
            }
            ApphudAttributionProvider.firebase -> {
                if (storage.firebase == body?.firebase_id) return
            }
        }

        ApphudLog.log("before start attribution request: $body")
        body?.let {
            coroutineScope.launch(errorHandler) {
                RequestManager.send(it) { attribution, error ->
                    ApphudLog.logI("Success without saving send attribution: $attribution")
                    launch(Dispatchers.Main) {
                        when (provider) {
                            ApphudAttributionProvider.appsFlyer -> {
                                val temporary = storage.appsflyer
                                storage.appsflyer = when {
                                    temporary == null -> AppsflyerInfo(
                                        id = body.appsflyer_id,
                                        data = body.appsflyer_data
                                    )
                                    temporary.id != body.appsflyer_id -> AppsflyerInfo(
                                        id = body.appsflyer_id,
                                        data = body.appsflyer_data
                                    )
                                    temporary.data != body.appsflyer_data -> AppsflyerInfo(
                                        id = body.appsflyer_id,
                                        data = body.appsflyer_data
                                    )
                                    else -> temporary
                                }
                            }
                            ApphudAttributionProvider.facebook -> {
                                val temporary = storage.facebook
                                storage.facebook = when {
                                    temporary == null -> FacebookInfo(body.facebook_data)
                                    temporary.data != body.facebook_data -> FacebookInfo(body.facebook_data)
                                    else -> temporary
                                }
                            }
                            ApphudAttributionProvider.firebase -> {
                                val temporary = storage.firebase
                                storage.firebase = when {
                                    temporary == null -> body.firebase_id
                                    temporary != body.firebase_id -> body.firebase_id
                                    else -> temporary
                                }
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

    internal fun setUserProperty(
        key: ApphudUserPropertyKey,
        value: Any?,
        setOnce: Boolean,
        increment: Boolean
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

        val property = ApphudUserProperty(key = key.key,
            value = value,
            increment = increment,
            setOnce = setOnce,
            type = typeString)

        pendingUserProperties.run {
            remove(property.key)
            put(property.key, property)
        }
        setNeedsToUpdateUserProperties = true
    }

    private fun updateUserProperties() {
        setNeedsToUpdateUserProperties = false
        if (pendingUserProperties.isEmpty()) return

        val properties = mutableListOf<Map<String, Any?>>()
        pendingUserProperties.forEach {
            properties.add(it.value.toJSON()!!)
        }

        val body = UserPropertiesBody(this.deviceId, properties)
        coroutineScope.launch(errorHandler) {
            RequestManager.userProperties(body) { userProperties, error ->
                launch(Dispatchers.Main) {
                    userProperties?.let{
                        if (userProperties.success) {
                            pendingUserProperties.clear()
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

    private fun getType(value: Any?): String {
        return when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is Float, is Double -> "float"
            is Int -> "integer"
            null -> "null"
            else -> "unknown"
        }
    }

    internal fun logout() {
        clear()
    }

    private fun clear() {
        RequestManager.cleanRegistration()

        skuDetailsIsLoaded.set(0)
        skuDetailsForFetchIsLoaded.set(0)
        skuDetailsForRestoreIsLoaded.set(0)
        paywallsDelayedCallback = null
        isRegistered = false
        storage.customer = null
        storage.userId = null
        storage.deviceId = null
        storage.advertisingId = null
        storage.isNeedSync = false
        storage.facebook = null
        storage.firebase = null
        storage.appsflyer = null
        storage.paywalls = null
        storage.productGroups = null
        generatedUUID = UUID.randomUUID().toString()
        prevPurchases.clear()
        tempPrevPurchases.clear()
        skuDetails.clear()
        allowIdentifyUser = true
        customProductsFetchedBlock = null
        pendingUserProperties.clear()
        setNeedsToUpdateUserProperties = false
        allowIdentifyUser = true
        didRetrievePaywallsAtThisLaunch = false
    }

    private fun updateUser(id: UserId?): UserId {
        val userId = when {
            id == null || id.isBlank() -> {
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
        val deviceId = when {
            id == null || id.isBlank() -> {
                storage.deviceId?.let { is_new = false; it } ?: generatedUUID
            }
            else -> {
                id
            }
        }
        storage.deviceId = deviceId
        return deviceId
    }

    internal fun getSkuDetailsList(): MutableList<SkuDetails>? {
        return skuDetails.takeIf { skuDetails.isNotEmpty() }
    }

    internal fun getSkuDetailsByProductId(productIdentifier: String): SkuDetails? {
        return getSkuDetailsList()?.let { skuList -> skuList.firstOrNull { it.sku == productIdentifier } }
    }

    private fun tryInvokePaywallsDelayedCallback() {
        ApphudLog.log("Try invoke paywalls delayed callback")
        if (!paywalls.isNullOrEmpty() && skuDetailsIsLoaded.isBothLoaded()) {
            setNeedsToUpdatePaywalls = false
            paywallsDelayedCallback?.invoke(paywalls, null)
            paywallsDelayedCallback = null
        } else {
            setNeedsToUpdatePaywalls = true
        }
    }

    internal fun getPaywalls(callback: PaywallCallback) {
        ApphudLog.log("Invoke getPaywalls")

        fetchPaywallsIfNeeded(true) { paywalls, error ->
            paywalls?.let {
                if (skuDetailsIsLoaded.isBothLoaded()) {
                    callback.invoke(paywalls, null)
                } else {
                    paywallsDelayedCallback = callback
                    setNeedsToUpdatePaywalls = true
                }
            } ?: run {
                val message = "Get Paywalls is failed with message = ${error?.message} and code = ${error?.errorCode}"
                ApphudLog.log(message = message)
                callback.invoke(null, error)
            }
        }
    }

    private fun fetchPaywallsIfNeeded(
        forceRefresh: Boolean = false,
        callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?) -> Unit
    ) {
        ApphudLog.log("try fetchPaywallsIfNeeded")

        if (!this.paywalls.isNullOrEmpty() && !forceRefresh) {
            ApphudLog.log("Using cached paywalls")
            callback(mutableListOf(*this.paywalls.toTypedArray()), null)
            return
        }

        coroutineScope.launch(errorHandler) {
            RequestManager.getPaywalls{ paywalls, error ->
                launch(Dispatchers.Main) {
                    paywalls?.let {
                        ApphudLog.logI("Paywalls loaded successfully")
                        processLoadedPaywalls(paywalls, true)
                        callback.invoke(paywalls, null)
                    }
                    error?.let {
                        callback.invoke(null, it)
                    }
                }
            }
        }
    }

    private fun processLoadedPaywalls(paywallsToCache : List<ApphudPaywall>, writeToCache: Boolean = true){
        updatePaywallsWithSkuDetails(paywallsToCache)

        this.paywalls.apply {
            clear()
            addAll(paywallsToCache)
        }

        if(writeToCache){
            cachePaywalls(paywalls = this.paywalls)
        }
    }

    private fun updatePaywallsWithSkuDetails(paywalls: List<ApphudPaywall>) {
        paywalls.forEach { paywall ->
            paywall.products?.forEach { product ->
                product.skuDetails = getSkuDetailsByProductId(product.product_id)
            }
        }
    }

    private fun updateGroupsWithSkuDetails(productGroups: List<ApphudGroup>) {
        productGroups.forEach { group ->
            group.products?.forEach { product ->
                product.skuDetails = getSkuDetailsByProductId(product.product_id)
            }
        }
    }

    private fun cachePaywalls(paywalls: List<ApphudPaywall>) {
        storage.paywalls = paywalls
    }

    private fun cachedPaywalls(): MutableList<ApphudPaywall> {
        val paywalls = storage.paywalls
        paywalls?.let {
            updatePaywallsWithSkuDetails(it)
        }
        return paywalls?.toMutableList() ?: mutableListOf()
    }

    private fun cacheGroups(groups: List<ApphudGroup>) {
        storage.productGroups = groups
    }

    private fun cachedGroups(): MutableList<ApphudGroup> {
        val productGroups = storage.productGroups
        productGroups?.let {
            updateGroupsWithSkuDetails(it)
        }
        return productGroups?.toMutableList() ?: mutableListOf()
    }

    fun paywallShown(paywall: ApphudPaywall?) {
        coroutineScope.launch(errorHandler) {
            RequestManager.paywallShown(paywall)
        }
    }

    fun paywallClosed(paywall: ApphudPaywall?) {
        coroutineScope.launch(errorHandler) {
            RequestManager.paywallClosed(paywall)
        }
    }

    private fun paywallCheckoutInitiated(paywall_id: String?, product_id: String?) {
        coroutineScope.launch(errorHandler) {
            RequestManager.paywallCheckoutInitiated(paywall_id, product_id)
        }
    }

    private fun paywallPaymentCancelled(paywall_id: String?, product_id: String?) {
        coroutineScope.launch(errorHandler) {
            RequestManager.paywallPaymentCancelled(paywall_id, product_id)
        }
    }
}