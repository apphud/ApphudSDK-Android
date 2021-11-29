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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {

    private const val MUST_REGISTER_ERROR = " :You must call the Apphud.start method once when your application starts before calling any other methods."

    private val builder = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()//need this to pass nullable values to JSON and from JSON
        .create()
    private val parser: Parser = GsonParser(builder)

    private lateinit var billing: BillingWrapper
    private val storage by lazy { SharedPreferencesStorage(context, parser) }
    private var generatedUUID = UUID.randomUUID().toString()
    private var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
    private var tempPrevPurchases = mutableSetOf<PurchaseRecordDetails>()
    private var productsForRestore = mutableListOf<PurchaseHistoryRecord>()
    private var skuDetails = mutableListOf<SkuDetails>()
    internal var productGroups: MutableList<ApphudGroup> = mutableListOf()
    internal var paywalls = mutableListOf<ApphudPaywall>()

    private val handler: Handler = Handler(Looper.getMainLooper())
    private val pendingUserProperties = mutableMapOf<String, ApphudUserProperty>()
    private val userPropertiesRunnable = Runnable {
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

    private var allowIdentifyUser = true
    private var didRetrievePaywallsAtThisLaunch = false
    private var is_new = true

    internal lateinit var userId: UserId
    private lateinit var deviceId: DeviceId
    private lateinit var apiKey: ApiKey
    private lateinit var context: Context

    internal var currentUser: Customer? = null
    internal var apphudListener: ApphudListener? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val errorHandler = CoroutineExceptionHandler { context, error ->
        error.message?.let { ApphudLog.logE(it) }
    }

    /**
     * 0 - we at start point without any skuDetails
     * 1 - we have only one loaded SkuType SUBS or INAPP
     * 2 - we have both loaded SkuType SUBS and INAPP
     * */
    private var skuDetailsForRestoreIsLoaded: AtomicInteger = AtomicInteger(0)
    private var purchasesForRestoreIsLoaded: AtomicInteger = AtomicInteger(0)
    private var customProductsFetchedBlock: ((List<SkuDetails>) -> Unit)? = null

    internal fun initialize(
        context: Context,
        apiKey: ApiKey,
        userId: UserId?,
        deviceId: DeviceId?
    ) {
        if (!allowIdentifyUser) {
            ApphudLog.logE(" " +
                "\n=============================================================" +
                "\nAbort initializing, because Apphud SDK already initialized." +
                "\nYou can only call `Apphud.start()` once per app lifecycle." +
                "\nOr if `Apphud.logout()` was called previously." +
                "\n=============================================================")
            return
        }

        ApphudLog.log("Start initialize with userId=$userId, deviceId=$deviceId")

        this.context = context
        this.apiKey = apiKey

        billing = BillingWrapper(context)
        
        val needRegistration = needRegistration()
        ApphudLog.logI("Need registration: " + needRegistration)

        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)
        RequestManager.setParams(this.context, this.userId, this.deviceId, this.apiKey)

        allowIdentifyUser = false
        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")

        skuDetails = cachedSkuDetails()
        productGroups = cachedGroups()
        paywalls = cachedPaywalls()

        if(needRegistration) {
            registration(this.userId, this.deviceId, null)
        }else{
            currentUser = storage.customer
            RequestManager.currentUser = currentUser
        }
    }

    private fun needRegistration(): Boolean{
        if(storage.userId.isNullOrEmpty()
            || storage.deviceId.isNullOrEmpty()
            || storage.customer == null
            || storage.needRegistration()) return true
        return false
    }

    private val mutex = Mutex()
    private var productsLoaded = AtomicInteger(0) //to know that products already loaded by another thread
    private fun registration(
        userId: UserId,
        deviceId: DeviceId,
        completionHandler: ((Customer?, ApphudError?) -> Unit)?
    ) {
        ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")
        coroutineScope.launch(errorHandler) {
            mutex.withLock {
                if(productsLoaded.get() == 0) {
                    if (fetchProducts()) {
                        launch(Dispatchers.Main) {
                            if (skuDetails.isNotEmpty()) {
                                apphudListener?.apphudFetchSkuDetailsProducts(skuDetails)
                                customProductsFetchedBlock?.invoke(skuDetails)
                            }
                        }
                    }
                }

                if(productsLoaded.get() > 0){
                    val customer = RequestManager.registrationSync(!didRetrievePaywallsAtThisLaunch, is_new)
                    customer?.let {
                        processCustomer(it)
                        storage.lastRegistration = System.currentTimeMillis()

                        launch(Dispatchers.Main) {
                            completionHandler?.invoke(it, null)
                            apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                            apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                            apphudListener?.paywallsDidLoadCallback(paywalls)

                            // try to resend purchases, if prev requests was fail
                            if (storage.isNeedSync) {
                                ApphudLog.log("registration: syncPurchases")
                                syncPurchases()
                            }

                            if (pendingUserProperties.isNotEmpty() && setNeedsToUpdateUserProperties) {
                                ApphudLog.log("registration: we should update UserProperties")
                                updateUserProperties()
                            }
                            ApphudLog.logI("End registration")
                        }
                    }?: run{
                        storage.customer?.let{
                            currentUser = it
                            launch(Dispatchers.Main) {
                                completionHandler?.invoke(it, null)
                            }
                        }?:run{
                            ApphudLog.logE("Registration error")
                            launch(Dispatchers.Main) {
                                completionHandler?.invoke(null, ApphudError("Registration error"))
                            }
                        }
                    }
                }else{
                    completionHandler?.invoke(null, ApphudError("Unable to restore products and sku details before registration at Apphud"))
                }
            }
        }
    }

    private suspend fun fetchProducts(): Boolean {
        val groupsList = RequestManager.allProducts()
        groupsList?.let { groups ->
            val result = fetchDetails(groups)
            if(result){
                //et to know to another threads that details are loaded successfully
                productsLoaded.incrementAndGet()

                //cache groups
                cacheGroups(groups)

                //reload products and paywall from cache to invalidate sku details inside
                productGroups = cachedGroups()
                paywalls = cachedPaywalls()
            }
            return result
        }
        return false
    }

    private suspend fun fetchDetails(groups :List<ApphudGroup>): Boolean {
        val ids = groups.map { it -> it.products?.map { it.product_id }!! }.flatten()

        var isInapLoaded = false
        var isSubsLoaded = false
        skuDetails.clear()

        coroutineScope {
            val subs = async{billing.detailsEx(BillingClient.SkuType.SUBS, ids)}
            val inap =  async{billing.detailsEx(BillingClient.SkuType.INAPP, ids)}

            subs.await()?.let {
                skuDetails.addAll(it)
                isSubsLoaded = true
            } ?: run {
                ApphudLog.logE("Unable to load SUBS details", false)
            }

            inap.await()?.let {
                skuDetails.addAll(it)
                isInapLoaded = true
            } ?: run {
                ApphudLog.logE("Unable to load INAP details", false)
            }

            cacheSkuDetails(skuDetails)
        }
        return isSubsLoaded && isInapLoaded
    }

    private fun processCustomer(customer: Customer){
        if (customer.paywalls.isNotEmpty()) {
            didRetrievePaywallsAtThisLaunch = true
            updatePaywallsWithSkuDetails(customer.paywalls)
            paywalls.clear()
            paywalls.addAll(customer.paywalls)
            cachePaywalls(paywalls)
        }

        currentUser = customer
        storage.updateCustomer(customer, apphudListener)
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
        checkRegistration{ error ->
            error?.let{
                callback?.invoke(ApphudPurchaseResult(null,null,null, error))

            }?: run{
                if (!productId.isNullOrEmpty()) {
                    //if we have productId
                    val sku = getSkuDetailsByProductId(productId)
                    if (sku != null) {
                        purchaseInternal(activity, sku, null, withValidation, callback)
                    } else {
                        coroutineScope.launch(errorHandler) {
                            fetchDetails(activity, productId, null, withValidation, callback)
                        }
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
                            coroutineScope.launch(errorHandler) {
                                fetchDetails(activity, null, product, withValidation, callback)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchDetails(
        activity: Activity,
        productId: String?,
        apphudProduct: ApphudProduct?,
        withValidation: Boolean,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        val productName: String = productId ?: apphudProduct?.product_id!!
        if(loadDetails(productName, apphudProduct)){
            getSkuDetailsByProductId(productName)?.let { sku ->
                //if we have not empty ApphudProduct
                apphudProduct?.let {
                    it.skuDetails = sku
                    purchaseInternal(activity, null, it, withValidation, callback)
                } ?: run {
                    purchaseInternal(activity, sku, null, withValidation, callback)
                }
            }
        }else{
            val message =
                "Unable to fetch product with given product id: $productName" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
            ApphudLog.log(message = message,sendLogToServer = true)
            callback?.invoke(ApphudPurchaseResult(null,null,null,ApphudError(message)))
        }
    }

    private suspend fun loadDetails(
        productId: String?,
        apphudProduct: ApphudProduct?) :Boolean
    {
        val productName: String = productId ?: apphudProduct?.product_id!!
        ApphudLog.log("Could not find SkuDetails for product id: $productName in memory")
        ApphudLog.log("Now try fetch it from Google Billing")

        return coroutineScope {
            var isInapLoaded = false
            var isSubsLoaded = false

            val subs = async { billing.detailsEx(BillingClient.SkuType.SUBS, listOf(productName)) }
            val inap = async { billing.detailsEx(BillingClient.SkuType.INAPP, listOf(productName)) }

            subs.await()?.let {
                skuDetails.addAll(it)
                isSubsLoaded = true

                ApphudLog.log("Google Billing return this info for product id = $productName :")
                it.forEach { ApphudLog.log("$it") }
            } ?: run {
                ApphudLog.logE("Unable to load SUBS details")
            }

            inap.await()?.let {
                skuDetails.addAll(it)
                isInapLoaded = true
                it.forEach { ApphudLog.log("$it") }
            } ?: run {
                ApphudLog.logE("Unable to load INAP details")
            }
            return@coroutineScope isSubsLoaded && isInapLoaded
        }
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

                        processCustomer(it)

                        storage.isNeedSync = false

                        if (newSubscriptions == null && newPurchases == null) {
                            val productId = details?.let { details.sku } ?: purchase.skus.first()?:"unknown"
                            val message = "Unable to validate purchase ($productId). " +
                                    "Ensure Google Service Credentials are correct and have necessary permissions. " +
                                    "Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support."

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
        checkRegistration{ error ->
            error?.let{
                callback.invoke(null, null, error)
            }?: run{
                syncPurchases(allowsReceiptRefresh = true, callback = callback)
            }
        }
    }

    internal fun syncPurchases(
        allowsReceiptRefresh: Boolean = false,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        checkRegistration{ error ->
            error?.let{
                callback?.invoke(null, null, error)
            }?: run{
                //storage.isNeedSync = true
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
                                processPurchasesHistoryResults(null, callback)
                            }
                        }
                    }
                }
                billing.queryPurchaseHistory(BillingClient.SkuType.SUBS)
                billing.queryPurchaseHistory(BillingClient.SkuType.INAPP)
            }
        }
    }

    private fun processPurchasesHistoryResults(
        message: String?,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        if (productsForRestore.isNullOrEmpty()) {
            message?.let{
                ApphudLog.log(message = it, sendLogToServer = true)
                callback?.invoke(null, null, ApphudError(message = it))
            }?:run{
                currentUser?.let{
                    callback?.invoke(it.subscriptions, it.purchases, null)
                }
            }
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
        checkRegistration{ error ->
            error?.let{
                val message = "Sync Purchases with Apphud is failed with message = ${error.message} and code = ${error.errorCode}"
                ApphudLog.logE(message = message)
                callback?.invoke(null, null, error)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    RequestManager.restorePurchases(tempPurchaseRecordDetails) { customer, error ->
                        launch(Dispatchers.Main) {
                            customer?.let{
                                if(tempPurchaseRecordDetails.size > it.subscriptions.size + it.purchases.size){
                                    val message = "Unable to restore purchases. " +
                                            "Ensure Google Service Credentials are correct and have necessary permissions. " +
                                            "Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support."
                                    ApphudLog.logE(message = message)
                                    callback?.invoke(null, null, ApphudError(message))
                                }else{
                                    prevPurchases.addAll(tempPurchaseRecordDetails)
                                    storage.isNeedSync = false
                                    storage.updateCustomer(it, apphudListener)

                                    ApphudLog.log("SyncPurchases: customer was updated $customer")
                                    apphudListener?.apphudSubscriptionsUpdated(it.subscriptions)
                                    apphudListener?.apphudNonRenewingPurchasesUpdated(it.purchases)
                                    callback?.invoke(it.subscriptions, it.purchases, null)
                                }
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

        checkRegistration{ error ->
            error?.let{
                ApphudLog.logE(it.message)
            }?: run{
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

        checkRegistration{ error ->
            error?.let{
                ApphudLog.logE(it.message)
            }?: run{
                val properties = mutableListOf<Map<String, Any?>>()
                synchronized(pendingUserProperties) {
                    pendingUserProperties.forEach {
                        properties.add(it.value.toJSON()!!)
                    }
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
        }
    }

    internal fun updateUserId(userId: UserId) {
        ApphudLog.log("Start updateUserId userId=$userId")

        checkRegistration{ error ->
            error?.let{
                ApphudLog.logE(it.message)
            }?: run{
                val id = updateUser(id = userId)
                this.userId = id

                RequestManager.setParams(this.context, this.userId, this.deviceId, this.apiKey)
                registration(this.userId, this.deviceId){ customer, error ->
                    customer?.let {
                        processCustomer(it)
                        ApphudLog.logI("End updateUserId customer=$customer")
                    }
                    error?.let{
                        ApphudLog.logE(it.message)
                    }
                }
            }
        }
    }

    internal fun subscriptions(callback: (List<ApphudSubscription>?, error: ApphudError?) -> Unit) {
        ApphudLog.log("Invoke subscriptions")

        checkRegistration{ error ->
            error?.let{
                callback.invoke(null, error)
            }?: run{
                callback.invoke(currentUser?.subscriptions?: emptyList(), null)
            }
        }
    }

    fun paywallShown(paywall: ApphudPaywall?) {
        checkRegistration{ error ->
            error?.let{
               ApphudLog.logI(error.message)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallShown(paywall)
                }
            }
        }
    }

    fun paywallClosed(paywall: ApphudPaywall?) {
        checkRegistration{ error ->
            error?.let{
                ApphudLog.logI(error.message)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallClosed(paywall)
                }
            }
        }
    }

    private fun paywallCheckoutInitiated(paywall_id: String?, product_id: String?) {
        checkRegistration{ error ->
            error?.let{
                ApphudLog.logI(error.message)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallCheckoutInitiated(paywall_id, product_id)
                }
            }
        }
    }

    private fun paywallPaymentCancelled(paywall_id: String?, product_id: String?) {
        checkRegistration{ error ->
            error?.let{
                ApphudLog.logI(error.message)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    RequestManager.paywallPaymentCancelled(paywall_id, product_id)
                }
            }
        }
    }

    private fun checkRegistration(callback: (ApphudError?) -> Unit){
        if(!isInitialized()) {
            callback.invoke(ApphudError(MUST_REGISTER_ERROR))
            return
        }

        currentUser?.let{
            callback.invoke(null)
        }?:run{
            registration(this.userId, this.deviceId){ _, error ->
                callback.invoke(error)
            }
        }
    }

    private fun isInitialized(): Boolean{
        return ::context.isInitialized
                && ::userId.isInitialized
                && ::deviceId.isInitialized
                && ::apiKey.isInitialized
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
        currentUser = null
        generatedUUID = UUID.randomUUID().toString()
        skuDetailsForRestoreIsLoaded.set(0)
        productsLoaded.set(0)
        customProductsFetchedBlock = null
        storage.clean()
        prevPurchases.clear()
        tempPrevPurchases.clear()
        skuDetails.clear()
        pendingUserProperties.clear()
        allowIdentifyUser = true
        didRetrievePaywallsAtThisLaunch = false
        setNeedsToUpdateUserProperties = false
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

    //SkuDetail cache ======================================
    internal fun getSkuDetailsList(): MutableList<SkuDetails>? {
        return skuDetails.takeIf { skuDetails.isNotEmpty() }
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

    internal fun getSkuDetailsByProductId(productIdentifier: String): SkuDetails? {
        return getSkuDetailsList()?.let { skuList -> skuList.firstOrNull { it.sku == productIdentifier } }
    }

    //Groups cache ======================================
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

    private fun updateGroupsWithSkuDetails(productGroups: List<ApphudGroup>) {
        productGroups.forEach { group ->
            group.products?.forEach { product ->
                product.skuDetails = getSkuDetailsByProductId(product.product_id)
            }
        }
    }

    //Paywalls cache ======================================
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

    private fun updatePaywallsWithSkuDetails(paywalls: List<ApphudPaywall>) {
        paywalls.forEach { paywall ->
            paywall.products?.forEach { product ->
                product.skuDetails = getSkuDetailsByProductId(product.product_id)
            }
        }
    }

}