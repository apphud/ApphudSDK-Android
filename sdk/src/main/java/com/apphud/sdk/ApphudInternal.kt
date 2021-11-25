package com.apphud.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.os.ConfigurationCompat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.body.*
import com.apphud.sdk.client.ApphudClient
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.ApphudSkuDetailsCallback
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.apphud.sdk.tasks.advertisingId
import com.google.gson.GsonBuilder
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
    private var client: ApphudClient? = null
    private lateinit var billing :BillingWrapper
    private val storage by lazy { SharedPreferencesStorage(context, parser) }
    private var generatedUUID = UUID.randomUUID().toString()
    private var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
    private var tempPrevPurchases = mutableSetOf<PurchaseRecordDetails>()
    private var productsForRestore = mutableListOf<PurchaseHistoryRecord>()
    internal var paywalls: MutableList<ApphudPaywall> = mutableListOf()
    internal var productGroups: MutableList<ApphudGroup> = mutableListOf()

    private var advertisingId: String? = null
        get() = storage.advertisingId
        set(value) {
            field = value
            if (storage.advertisingId != value) {
                storage.advertisingId = value
                ApphudLog.log("advertisingId = $advertisingId is fetched and saved")
            }
            ApphudLog.log("advertisingId: continue registration")
            registration(userId, deviceId)
        }

    private var allowIdentifyUser = true
    private var isRegistered = false
    private var didRetrievePaywallsAtThisLaunch = false

    internal var userId: UserId? = null
    private lateinit var deviceId: DeviceId

    private var is_new = true

    private lateinit var apiKey: ApiKey
    private lateinit var context: Context

    internal val currentUser: Customer?
        get() = storage.customer
    internal var apphudListener: ApphudListener? = null

    private var skuDetails = mutableListOf<SkuDetails>()

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

    private fun loadAdsId() {
        if (ApphudUtils.adTracking) {
            AdvertisingTask().execute()
        }
    }

    private class AdvertisingTask : AsyncTask<Void, Void, String?>() {
        override fun doInBackground(vararg params: Void?): String? = advertisingId(context)
        override fun onPostExecute(result: String?) {
            advertisingId = result
        }
    }

    internal fun updateUserId(userId: UserId) {
        ApphudLog.log("Start updateUserId userId=$userId")
        val id = updateUser(id = userId)
        this.userId = id

        val body = mkRegistrationBody(id, deviceId)
        client?.registrationUser(body) { customer ->
            handler.post {
                storage.updateCustomer(customer, apphudListener)
                ApphudLog.log("End updateUserId customer=$customer")
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
            ApphudLog.logI(" " +
                    "\n=============================================================" +
                    "\nAbort initializing, because Apphud SDK already initialized." +
                    "\nYou can only call `Apphud.start()` once per app lifecycle." +
                    "\nOr if `Apphud.logout()` was called previously." +
                    "\n=============================================================")
            return
        }
        this.apiKey = apiKey
        this.context = context
        billing = BillingWrapper(context)
        client = ApphudClient(apiKey, parser)
        client?.let { ApphudLog.setClient(it) }
        allowIdentifyUser = false

        ApphudLog.log("Restore paywall and product groups from cache")
        this.paywalls = cachedPaywalls()
        this.productGroups = cachedGroups()

        ApphudLog.log("Start initialize with userId=$userId, deviceId=$deviceId")
        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)
        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")
        if (ApphudUtils.adTracking)
            loadAdsId()
        else
            registration(this.userId, this.deviceId)
    }

    private fun needRegistration(): Boolean{
        if(storage.userId.isNullOrEmpty()
            || storage.deviceId.isNullOrEmpty()
            || storage.customer == null
            || storage.needRegistration()) return true
        return false
    }

    private fun fetchProducts() {
        try {
            client?.allProducts { groups ->
                ApphudLog.log("fetchProducts: products from Apphud server: $groups")
                cacheGroups(groups)
                fetchDetails(groups)
            }
        }catch(ex: Exception){
            ApphudLog.logE("Fetch products failed : " + ex.message, false)
        }
    }

    private fun fetchDetails(groups: List<ApphudGroup>) {
        try {
            val ids = groups.map { it -> it.products?.map { it.product_id }!! }.flatten()
            if(ids.isNotEmpty()){
                skuDetails.clear()
                billing.skuCallback = { details ->
                    ApphudLog.log("fetchDetails: details from Google Billing: $details")
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
                billing.details(BillingClient.SkuType.SUBS, ids)
                billing.details(BillingClient.SkuType.INAPP, ids)
            }
        }catch(ex: Exception){
            ApphudLog.logE("Fetch products sku details failed : " + ex.message, false)
        }
    }

    private fun registration(
        userId: UserId?,
        deviceId: DeviceId?
    ) {
        ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")

        if(needRegistration()) {
            fetchProducts()

            val body = mkRegistrationBody(userId!!, this.deviceId)
            client?.registrationUser(body) { customer ->
                isRegistered = true
                handler.post {
                    ApphudLog.log("registration: registrationUser customer=$customer")
                    storage.lastRegistration = System.currentTimeMillis()
                    storage.updateCustomer(customer, apphudListener)
                    apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                    apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)

                    if (customer.paywalls.isNotEmpty()) {
                        didRetrievePaywallsAtThisLaunch = true
                        processLoadedPaywalls(customer.paywalls)
                        apphudListener?.paywallsDidLoadCallback(this.paywalls)
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

                    if (fetchPaywallsDelayedCallback != null) {
                        fetchPaywallsDelayedCallback?.invoke()
                        fetchPaywallsDelayedCallback = null
                    }
                }
            }
        } else{
            if(this.productGroups.isNotEmpty()) {
                fetchDetails(this.productGroups)
            }else{
                fetchProducts()
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
        val purchaseBody = details?.let { makePurchaseBody(purchase, it, null, null) }
            ?: apphudProduct?.let { makePurchaseBody(purchase, it.skuDetails, it.paywall_id, it.id) }
        if (purchaseBody == null) {
            val message = "SkuDetails and ApphudProduct can not be null at the same time" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
            ApphudLog.log(message = message)

            callback?.invoke(ApphudPurchaseResult(null,
                null,
                null,
                ApphudError(message)))
        } else {
            storage.isNeedSync = true
            client?.purchased(purchaseBody) { customer, errors ->
                handler.post {
                    when (errors) {
                        null -> {
                            ApphudLog.log("client.purchased: $customer")

                            val newSubscriptions =
                                customer?.subscriptions?.firstOrNull { it.productId == purchase.skus.first() }

                            val newPurchases =
                                customer?.purchases?.firstOrNull { it.productId == purchase.skus.first() }

                            customer?.let {
                                storage.updateCustomer(it, apphudListener)
                            }
                            storage.isNeedSync = false

                            if (newSubscriptions == null && newPurchases == null) {
                                val message =
                                    "Error! There are no new subscriptions " +
                                            "or new purchases from the Apphud server " +
                                            "after the purchase of ${purchaseBody.purchases.first().product_id}"
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
                        else -> {
                            val message = "Unable to validate purchase with error = ${errors.message} and code = ${errors.errorCode}" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                            ApphudLog.log(message = message)
                            callback?.invoke(ApphudPurchaseResult(null,
                                null,
                                purchase,
                                errors)
                            )
                        }
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
        client?.purchased(makeRestorePurchasesBody(tempPurchaseRecordDetails.toList())) { customer, errors ->
            handler.post {
                when (errors) {
                    null -> {
                        prevPurchases.addAll(tempPurchaseRecordDetails)
                        storage.isNeedSync = false
                        customer?.let{
                            storage.updateCustomer(it, apphudListener)
                        }
                        ApphudLog.log("SyncPurchases: customer was updated $customer")
                        apphudListener?.apphudSubscriptionsUpdated(customer?.subscriptions!!)
                        apphudListener?.apphudNonRenewingPurchasesUpdated(customer?.purchases!!)
                        callback?.invoke(customer?.subscriptions, customer?.purchases, null)
                    }
                    else -> {
                        val message =
                            "Sync Purchases with Apphud is failed with message = ${errors.message} and code = ${errors.errorCode}"
                        ApphudLog.log(message = message)
                        callback?.invoke(null, null, errors)
                    }
                }
            }
            ApphudLog.log("SyncPurchases: success send history purchases ${tempPurchaseRecordDetails.toList()}")
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
            client?.send(body) { attribution ->
                ApphudLog.log("Success without saving send attribution: $attribution")
                handler.post {
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

        synchronized(pendingUserProperties){
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

        val properties = mutableListOf<Map<String, Any?>>()

        synchronized(pendingUserProperties) {
            pendingUserProperties.forEach {
                properties.add(it.value.toJSON()!!)
            }
        }

        val body = UserPropertiesBody(this.deviceId, properties)
        client?.userProperties(body) { userProperties ->
            handler.post {
                if (userProperties.success) {
                    pendingUserProperties.clear()
                    ApphudLog.log("User Properties successfully updated.")
                } else {
                    val message = "User Properties update failed with errors"
                    ApphudLog.logE(message)
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
        storage.skuDetails = null
        storage.lastRegistration = 0L
        userId = null
        generatedUUID = UUID.randomUUID().toString()
        prevPurchases.clear()
        tempPrevPurchases.clear()
        skuDetails.clear()
        allowIdentifyUser = true
        customProductsFetchedBlock = null
        pendingUserProperties.clear()
        setNeedsToUpdateUserProperties = false
        client = null
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

    private fun makePurchaseBody(
        purchase: Purchase,
        details: SkuDetails?,
        paywall_id: String?,
        apphud_product_id: String?
    ) =
        PurchaseBody(
            device_id = deviceId,
            purchases = listOf(
                PurchaseItemBody(
                    order_id = purchase.orderId,
                    product_id = details?.let { details.sku } ?: purchase.skus.first(),
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = details?.priceCurrencyCode,
                    price_amount_micros = details?.priceAmountMicros,
                    subscription_period = details?.subscriptionPeriod,
                    paywall_id = paywall_id,
                    product_bundle_id = apphud_product_id
                )
            )
        )

    private fun makeRestorePurchasesBody(purchases: List<PurchaseRecordDetails>) =
        PurchaseBody(
            device_id = deviceId,
            purchases = purchases.map { purchase ->
                PurchaseItemBody(
                    order_id = null,
                    product_id = purchase.details.sku,
                    purchase_token = purchase.record.purchaseToken,
                    price_currency_code = purchase.details.priceCurrencyCode,
                    price_amount_micros = purchase.details.priceAmountMicros,
                    subscription_period = purchase.details.subscriptionPeriod,
                    paywall_id = null,
                    product_bundle_id = null
                )
            }
        )

    private fun mkRegistrationBody(userId: UserId, deviceId: DeviceId) =
        RegistrationBody(
            locale = ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0).toString(),
            sdk_version = BuildConfig.VERSION_NAME,
            app_version = context.buildAppVersion(),
            device_family = Build.MANUFACTURER,
            platform = "Android",
            device_type = Build.MODEL,
            os_version = Build.VERSION.RELEASE,
            start_app_version = context.buildAppVersion(),
            idfv = null,
            idfa = if (ApphudUtils.adTracking) advertisingId else null,
            user_id = userId,
            device_id = deviceId,
            time_zone = TimeZone.getDefault().id,
            is_sandbox = context.isDebuggable(),
            is_new = this.is_new,
            need_paywalls = !didRetrievePaywallsAtThisLaunch
        )

    internal fun makeErrorLogsBody(message: String, apphud_product_id: String? = null) =
        ErrorLogsBody(
            message = message,
            bundle_id = apphud_product_id,
            user_id = userId,
            device_id = deviceId,
            environment = if (context.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis()
        )

    internal fun getSkuDetailsList(): MutableList<SkuDetails>? {
        return skuDetails.takeIf { skuDetails.isNotEmpty() }
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

        if(skuDetailsIsLoaded.isBothLoaded()){
            callback.invoke(this.paywalls, null)
        }else{
            paywallsDelayedCallback = callback
            setNeedsToUpdatePaywalls = true
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

    internal fun getSkuDetailsByProductId(productIdentifier: String): SkuDetails? {
        return getSkuDetailsList()?.let { skuList -> skuList.firstOrNull { it.sku == productIdentifier } }
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

    private fun cacheSkuDetails(details: List<SkuDetails>) {
        val skuDetailsToCache :MutableList<String> = mutableListOf()
        details.forEach{
            skuDetailsToCache.add(it.originalJson)
        }
        storage.skuDetails = skuDetailsToCache
    }

    private fun cachedSkuDetails(): MutableList<SkuDetails> {
        val result :MutableList<SkuDetails> = mutableListOf()
        try{
            val skuDetailsFromCache = storage.skuDetails?.toMutableList() ?: mutableListOf()
            skuDetailsFromCache.forEach{
                result.add(SkuDetails(it))
            }
        }catch (ex: Exception){
            ex.message?.let{
                ApphudLog.logE(it)
            }
        }
        return result
    }

    fun paywallShown(paywall: ApphudPaywall?) {
        client?.trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_shown",
                paywall_id = paywall?.id
            )
        )
    }

    fun paywallClosed(paywall: ApphudPaywall?) {
        client?.trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_closed",
                paywall_id = paywall?.id
            )
        )
    }

    private fun paywallCheckoutInitiated(paywall_id: String?, product_id: String?) {
        client?.trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_checkout_initiated",
                paywall_id = paywall_id,
                product_id = product_id
            )
        )
    }

    private fun paywallPaymentCancelled(paywall_id: String?, product_id: String?) {
        client?.trackPaywallEvent(
            makePaywallEventBody(
                name = "paywall_payment_cancelled",
                paywall_id = paywall_id,
                product_id = product_id
            )
        )
    }

    private fun makePaywallEventBody(name: String, paywall_id: String? = null, product_id: String? = null): PaywallEventBody {
        val properties = mutableMapOf<String, Any>()
        paywall_id?.let { properties.put("paywall_id", it) }
        product_id?.let { properties.put("product_id", it) }
        return PaywallEventBody(
            name = name,
            user_id = userId,
            device_id = deviceId,
            environment = if (context.isDebuggable()) "sandbox" else "production",
            timestamp = System.currentTimeMillis(),
            properties = if (properties.isNotEmpty()) properties else null
        )
    }
}