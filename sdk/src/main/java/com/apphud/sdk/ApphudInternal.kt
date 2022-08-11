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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {

    //region === Variables ===
    private const val MUST_REGISTER_ERROR = " :You must call `Apphud.start` method before calling any other methods."

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
    private var didRegisterCustomerAtThisLaunch = false
    private var is_new = true

    internal lateinit var userId: UserId
    lateinit var deviceId: DeviceId
    private lateinit var apiKey: ApiKey
    private lateinit var context: Context

    internal var currentUser: Customer? = null
    internal var apphudListener: ApphudListener? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val errorHandler = CoroutineExceptionHandler { context, error ->
        error.message?.let { ApphudLog.logE(it) }
    }

    private var customProductsFetchedBlock: ((List<SkuDetails>) -> Unit)? = null
    private var skuDetailsForRestoreIsLoaded_SUBS: AtomicBoolean = AtomicBoolean(false)
    private var skuDetailsForRestoreIsLoaded_INAPP: AtomicBoolean = AtomicBoolean(false)
    private var purchasesForRestoreIsLoaded_SUBS: AtomicBoolean = AtomicBoolean(false)
    private var purchasesForRestoreIsLoaded_INAPP: AtomicBoolean = AtomicBoolean(false)
    private var isSyncing: AtomicBoolean = AtomicBoolean(false)

    //endregion

    //region === Start ===
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

        ApphudLog.log("Start initialization with userId=$userId, deviceId=$deviceId")
        if(apiKey.isEmpty()) throw Exception("ApiKey can't be empty")

        this.context = context
        this.apiKey = apiKey

        billing = BillingWrapper(context)
        
        val needRegistration = needRegistration(userId)

        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)
        RequestManager.setParams(this.context, this.userId, this.deviceId, this.apiKey)

        allowIdentifyUser = false
        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")


        //Restore from cache
        this.currentUser = storage.customer
        this.productGroups = readGroupsFromCache()
        this.paywalls = readPaywallsFromCache()

        loadProducts()

        if(needRegistration) {
            registration(this.userId, this.deviceId, true, null)
        }else{
            notifyLoadingCompleted(storage.customer, null, true)
        }
    }

    internal fun refreshEntitlements(){

        val hasPurchases = currentUser?.let{
            !(it.purchases.isEmpty() && it.subscriptions.isEmpty())
        }?: false

        if(hasPurchases && didRegisterCustomerAtThisLaunch){
            registration(this.userId, this.deviceId, true, null)
        }
    }
    //endregion

    //region === Registration ===
    private fun needRegistration(passedUserId: String?): Boolean{
        passedUserId?.let{
            if(!storage.userId.isNullOrEmpty()){
                if(it != storage.userId) {
                    return true
                }
            }
        }
        if(storage.userId.isNullOrEmpty()
            || storage.deviceId.isNullOrEmpty()
            || storage.customer == null
            || storage.paywalls == null
            || storage.needRegistration()) return true
        return false
    }

    private val mutexProducts = Mutex()
    private fun loadProducts(){
        coroutineScope.launch(errorHandler) {
            mutexProducts.withLock {
                async {
                    if (productsLoaded.get() == 0) {
                        if (fetchProducts()) {
                            //Let to know to another threads that details are loaded successfully
                            productsLoaded.incrementAndGet()

                            launch(Dispatchers.Main) {
                                notifyLoadingCompleted(null, skuDetails)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchProducts(): Boolean {
        val cachedGroups = storage.productGroups
        if(cachedGroups == null || storage.needUpdateProductGroups()){
            val groupsList = RequestManager.allProducts()
            groupsList?.let { groups ->
                cacheGroups(groups)
                return fetchDetails(groups)
            }
        }else{
            return fetchDetails(cachedGroups)
        }
        return false
    }

    private suspend fun fetchDetails(groups :List<ApphudGroup>): Boolean {
        val ids = groups.map { it -> it.products?.map { it.product_id }!! }.flatten()

        var isInapLoaded = false
        var isSubsLoaded = false
        synchronized(skuDetails) {
            skuDetails.clear()
        }

        coroutineScope {
            val subs = async{billing.detailsEx(BillingClient.SkuType.SUBS, ids)}
            val inap =  async{billing.detailsEx(BillingClient.SkuType.INAPP, ids)}

            subs.await()?.let {
                synchronized(skuDetails) {
                    skuDetails.addAll(it)
                }
                isSubsLoaded = true
            } ?: run {
                ApphudLog.logE("Unable to load SUBS details", false)
            }

            inap.await()?.let {
                synchronized(skuDetails) {
                    skuDetails.addAll(it)
                }
                isInapLoaded = true
            } ?: run {
                ApphudLog.logE("Unable to load INAP details", false)
            }
        }
        return isSubsLoaded && isInapLoaded
    }

    private var notifyFullyLoaded = false
    @Synchronized
    private fun notifyLoadingCompleted(customerLoaded: Customer? = null, skuDetailsLoaded: List<SkuDetails>? = null, fromCache: Boolean = false){
        var restorePaywalls = true

        skuDetailsLoaded?.let{
            productGroups = readGroupsFromCache()
            updateGroupsWithSkuDetails(productGroups)

            //notify that skuDetails are loaded
            apphudListener?.apphudFetchSkuDetailsProducts(getSkuDetailsList())
            customProductsFetchedBlock?.invoke(getSkuDetailsList())
        }

        customerLoaded?.let{
            if(fromCache){
                RequestManager.currentUser = it
                notifyFullyLoaded = true
            }else{
                if (it.paywalls.isNotEmpty()) {
                    notifyFullyLoaded = true
                    cachePaywalls(it.paywalls)
                }else{
                    /* Attention:
                     * If customer loaded without paywalls, do not reload paywalls from cache!
                     * If cache time is over, paywall from cach will be NULL
                    */
                    restorePaywalls = false
                }
                storage.updateCustomer(it, apphudListener)
            }

            currentUser = it
            userId = it.user.userId
            if (!didRegisterCustomerAtThisLaunch) {
                currentUser?.let{ c ->
                    apphudListener?.userDidRegister(c.user)
                }
            }
            didRegisterCustomerAtThisLaunch = true

            if (restorePaywalls) {
                paywalls = readPaywallsFromCache()
            }

            apphudListener?.apphudNonRenewingPurchasesUpdated(currentUser!!.purchases)
            apphudListener?.apphudSubscriptionsUpdated(currentUser!!.subscriptions)
        }

        updatePaywallsWithSkuDetails(paywalls)

        if(restorePaywalls && currentUser != null && paywalls.isNotEmpty() && skuDetails.isNotEmpty() && notifyFullyLoaded){
            notifyFullyLoaded = false
            apphudListener?.paywallsDidFullyLoad(paywalls)
        }
    }

    private val mutex = Mutex()
    private var productsLoaded = AtomicInteger(0) //to know that products already loaded by another thread
    private fun registration(
        userId: UserId,
        deviceId: DeviceId,
        forceRegistration: Boolean = false,
        completionHandler: ((Customer?, ApphudError?) -> Unit)?
    ) {
        ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")
        coroutineScope.launch(errorHandler) {
            var customer: Customer? = null
            var repeatRegistration: Boolean? = false
            mutex.withLock {
                if(currentUser == null || forceRegistration) {
                    val threads = listOf(
                        async {
                            customer = RequestManager.registrationSync(
                                !didRegisterCustomerAtThisLaunch,
                                is_new,
                                forceRegistration
                            )
                        },
                        async {
                            repeatRegistration = fetchAdvertisingId()
                        }
                    )
                    threads.awaitAll().let {
                        customer?.let {
                            storage.lastRegistration = System.currentTimeMillis()

                            if(repeatRegistration == true) {
                                repeatRegistrationSilent()
                            }

                            launch(Dispatchers.Main) {
                                notifyLoadingCompleted(it)
                                completionHandler?.invoke(it, null)

                                if (pendingUserProperties.isNotEmpty() && setNeedsToUpdateUserProperties) {
                                    updateUserProperties()
                                }
                            }

                            if(storage.isNeedSync) {
                                coroutineScope.launch(errorHandler) {
                                    syncPurchases()
                                }
                            }

                        } ?: run {
                            ApphudLog.logE("Registration: error")
                            launch(Dispatchers.Main) {
                                completionHandler?.invoke(
                                    null,
                                    ApphudError("Registration: error")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun repeatRegistrationSilent(){
        val customerNew = RequestManager.registrationSync(
            !didRegisterCustomerAtThisLaunch,
            is_new,
            true
        )
    }

    private suspend fun fetchAdvertisingId(): Boolean{
        val advertisingId = RequestManager.fetchAdvertisingId()
        advertisingId?.let{
            if(RequestManager.advertisingId.isNullOrEmpty() || RequestManager.advertisingId != it){
                RequestManager.advertisingId = it
                return true
            }
            return false
        }
        return false
    }

    internal fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit) {
        customProductsFetchedBlock = callback
        if (skuDetails.isNotEmpty()) {
            customProductsFetchedBlock?.invoke(skuDetails)
        }
    }
    //endregion

    //region === Purchases ===
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
                    var message =
                        if (details != null) {
                            "Unable to buy product with given product id: ${details.sku} "
                        } else {
                            paywallPaymentCancelled(apphudProduct?.paywall_id, apphudProduct?.product_id, purchasesResult.result.responseCode)
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

                    processPurchaseError(purchasesResult)
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
                billing.purchase(activity, details, deviceId)
            }
            apphudProduct?.skuDetails != null -> {
                paywallCheckoutInitiated(apphudProduct.paywall_id, apphudProduct.product_id)
                billing.purchase(activity, apphudProduct.skuDetails!!, deviceId)
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

    private fun processPurchaseError(status:  PurchaseUpdatedCallbackStatus.Error){
        if(status.result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            storage.isNeedSync = true
            coroutineScope.launch(errorHandler) {
                syncPurchases()
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

                        notifyLoadingCompleted(it)

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
    //endregion

    //region === Restore purchases ===
    internal fun restorePurchases(callback: ApphudPurchasesRestoreCallback) {
        checkRegistration{ error ->
            error?.let{
                callback.invoke(null, null, error)
            }?: run{
                syncPurchases(observerMode = false, callback = callback)
            }
        }
    }


    internal fun syncPurchases(
        paywallIdentifier: String? = null,
        observerMode: Boolean = true,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        if(!isSyncing.get()){
            isSyncing.set(true)

            checkRegistration{ error ->
                error?.let{
                    callback?.invoke(null, null, error)
                    isSyncing.set(false)
                }?: run{
                    productsForRestore.clear()
                    tempPrevPurchases.clear()

                    purchasesForRestoreIsLoaded_SUBS.set(false)
                    purchasesForRestoreIsLoaded_INAPP.set(false)
                    skuDetailsForRestoreIsLoaded_SUBS.set(false)
                    skuDetailsForRestoreIsLoaded_INAPP.set(false)

                    billing.restoreCallback = { restoreStatus ->
                        if(restoreStatus.type() == BillingClient.SkuType.SUBS){
                            skuDetailsForRestoreIsLoaded_SUBS.set(true)
                        } else if(restoreStatus.type() == BillingClient.SkuType.INAPP){
                            skuDetailsForRestoreIsLoaded_INAPP.set(true)
                        }

                        when (restoreStatus) {
                            is PurchaseRestoredCallbackStatus.Error -> {
                                val type = if(restoreStatus.type() == BillingClient.SkuType.SUBS) "subscriptions" else "in-app products"
                                ApphudLog.log("Failed to restore purchases for $type with error: ("
                                        + "${restoreStatus.result?.responseCode})"
                                        + "${restoreStatus.message})")

                                if (skuDetailsForRestoreIsLoaded_SUBS.get() && skuDetailsForRestoreIsLoaded_INAPP.get()) {
                                    if (tempPrevPurchases.isEmpty()) {
                                        val error =
                                            ApphudError(message = "Restore Purchases is failed for SkuType.SUBS and SkuType.INAPP",
                                                secondErrorMessage = restoreStatus.message,
                                                errorCode = restoreStatus.result?.responseCode)
                                        ApphudLog.log(message = error.toString(), sendLogToServer = true)
                                        callback?.invoke(null, null, error)
                                        isSyncing.set(false)
                                    } else {
                                        syncPurchasesWithApphud(paywallIdentifier, tempPrevPurchases, callback, observerMode)
                                    }
                                }
                            }
                            is PurchaseRestoredCallbackStatus.Success -> {
                                ApphudLog.log("SyncPurchases: purchases was restored: ${restoreStatus.purchases}")
                                tempPrevPurchases.addAll(restoreStatus.purchases)

                                if (skuDetailsForRestoreIsLoaded_SUBS.get() && skuDetailsForRestoreIsLoaded_INAPP.get()) {
                                    billing.restoreCallback = null
                                    if (observerMode && prevPurchases.containsAll(tempPrevPurchases)) {
                                        ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                                        isSyncing.set(false)
                                    } else {
                                        syncPurchasesWithApphud(paywallIdentifier, tempPrevPurchases, callback, observerMode)
                                    }
                                }
                            }
                        }
                    }
                    billing.historyCallback = { purchasesHistoryStatus ->
                        if(purchasesHistoryStatus.type() == BillingClient.SkuType.SUBS){
                            purchasesForRestoreIsLoaded_SUBS.set(true)
                        } else if(purchasesHistoryStatus.type() == BillingClient.SkuType.INAPP){
                            purchasesForRestoreIsLoaded_INAPP.set(true)
                        }

                        when (purchasesHistoryStatus) {
                            is PurchaseHistoryCallbackStatus.Error -> {

                                val type = if(purchasesHistoryStatus.type() == BillingClient.SkuType.SUBS) "subscriptions" else "in-app products"
                                ApphudLog.log("Failed to load history for $type with error: ("
                                        + "${purchasesHistoryStatus.result?.responseCode})"
                                        + "${purchasesHistoryStatus.result?.debugMessage})")

                                if (purchasesForRestoreIsLoaded_SUBS.get() && purchasesForRestoreIsLoaded_INAPP.get()) {
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

                                if (purchasesForRestoreIsLoaded_SUBS.get() && purchasesForRestoreIsLoaded_INAPP.get()) {
                                    billing.historyCallback = null
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
    }

    private fun processPurchasesHistoryResults(
        message: String?,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        if (productsForRestore.isNullOrEmpty()) {
            isSyncing.set(false)
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

    private fun findJustPurchasedProduct(paywallIdentifier: String?, tempPurchaseRecordDetails: Set<PurchaseRecordDetails>): ApphudProduct?{
        try {
            paywallIdentifier?.let {
                getPaywalls().firstOrNull { it.identifier == paywallIdentifier }
                    ?.let { currentPaywall ->
                        val record = tempPurchaseRecordDetails.toList()
                            .maxByOrNull { it.record.purchaseTime }
                        record?.let { rec ->
                            val offset = System.currentTimeMillis() - rec.record.purchaseTime
                            if (offset < 300000L) { // 5 min
                                return currentPaywall.products?.find { it.skuDetails?.sku == rec.details.sku }
                            }
                        }
                    }
            }
        }catch (ex: Exception){
            ex.message?.let{
                ApphudLog.logE(message = it)
            }
        }
        return null
    }

    private fun syncPurchasesWithApphud(
        paywallIdentifier: String? = null,
        tempPurchaseRecordDetails: Set<PurchaseRecordDetails>,
        callback: ApphudPurchasesRestoreCallback? = null,
        observerMode: Boolean
    ) {
        checkRegistration{ error ->
            error?.let{
                val message = "Sync Purchases with Apphud is failed with message = ${error.message} and code = ${error.errorCode}"
                ApphudLog.logE(message = message)
                callback?.invoke(null, null, error)
                isSyncing.set(false)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    val apphudProduct: ApphudProduct? = findJustPurchasedProduct(paywallIdentifier, tempPurchaseRecordDetails)
                    RequestManager.restorePurchases(apphudProduct, tempPurchaseRecordDetails, observerMode) { customer, error ->
                        launch(Dispatchers.Main) {
                            customer?.let{
                                if(tempPurchaseRecordDetails.size > 0 && (it.subscriptions.size + it.purchases.size) == 0) {
                                    val message = "Unable to completely validate all purchases. " +
                                            "Ensure Google Service Credentials are correct and have necessary permissions. " +
                                            "Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support."
                                    ApphudLog.logE(message = message)
                                }else{
                                    ApphudLog.log("SyncPurchases: customer was successfully updated $customer")
                                }

                                storage.isNeedSync = false

                                prevPurchases.addAll(tempPurchaseRecordDetails)
                                userId = customer.user.userId
                                storage.updateCustomer(it, apphudListener)

                                currentUser = storage.customer
                                RequestManager.currentUser = currentUser

                                ApphudLog.log("SyncPurchases: customer was updated $customer")
                                apphudListener?.apphudSubscriptionsUpdated(it.subscriptions)
                                apphudListener?.apphudNonRenewingPurchasesUpdated(it.purchases)
                                callback?.invoke(it.subscriptions, it.purchases, null)
                            }
                            error?.let {
                                val message = "Sync Purchases with Apphud is failed with message = ${error.message} and code = ${error.errorCode}"
                                ApphudLog.logE(message = message)
                                callback?.invoke(null, null, error)
                            }
                            isSyncing.set(false)
                        }
                        ApphudLog.log("SyncPurchases: success send history purchases ${tempPurchaseRecordDetails.toList()}")
                    }
                }
            }
        }
    }
    //endregion

    //region === Attribution ===
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
                    (temporary.id == body?.appsflyer_id) && (temporary.data == body?.appsflyer_data)-> {
                        ApphudLog.logI("Already submitted the same AppsFlyer attribution, skipping")
                        return
                    }
                }
            }
            ApphudAttributionProvider.facebook -> {
                val temporary = storage.facebook
                when {
                    temporary == null -> Unit
                    temporary.data == body?.facebook_data -> {
                        ApphudLog.logI("Already submitted the same Facebook attribution, skipping")
                        return
                    }
                }
            }
            ApphudAttributionProvider.firebase -> {
                if (storage.firebase == body?.firebase_id) {
                    ApphudLog.logI("Already submitted the same Firebase attribution, skipping")
                    return
                }
            }
            ApphudAttributionProvider.adjust -> {
                val temporary = storage.adjust
                when {
                    temporary == null -> Unit
                    (temporary.adid == body?.adid) && (temporary.adjust_data == body?.adjust_data)-> {
                        ApphudLog.logI("Already submitted the same Adjust attribution, skipping")
                        return
                    }
                }
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
                            ApphudLog.logI("Did send $attribution attribution data to Apphud")
                            launch(Dispatchers.Main) {
                                when (provider) {
                                    ApphudAttributionProvider.appsFlyer -> {
                                        val temporary = storage.appsflyer
                                        storage.appsflyer = when {
                                            temporary == null -> AppsflyerInfo(
                                                id = body.appsflyer_id,
                                                data = body.appsflyer_data
                                            )
                                            (temporary.id != body.appsflyer_id) || (temporary.data != body.appsflyer_data)-> AppsflyerInfo(
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
                                    ApphudAttributionProvider.adjust -> {
                                        val temporary = storage.adjust
                                        storage.adjust = when {
                                            temporary == null -> AdjustInfo(
                                                adid = body.adid,
                                                adjust_data = body.adjust_data
                                            )
                                            (temporary.adid != body.adid) || (temporary.adjust_data != body.adjust_data)-> AdjustInfo(
                                                adid = body.adid,
                                                adjust_data = body.adjust_data
                                            )
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
    //endregion

    //region === User Properties ===
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

        if(!storage.needSendProperty(property)){
            return
        }

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
                val sentPropertiesForSave = mutableListOf<ApphudUserProperty>()

                synchronized(pendingUserProperties) {
                    pendingUserProperties.forEach {
                        properties.add(it.value.toJSON()!!)
                        if(!it.value.increment && it.value.value != null) {
                            sentPropertiesForSave.add(it.value)
                        }
                    }
                }

                val body = UserPropertiesBody(this.deviceId, properties)
                coroutineScope.launch(errorHandler) {
                    RequestManager.userProperties(body) { userProperties, error ->
                        launch(Dispatchers.Main) {
                            userProperties?.let{
                                if (userProperties.success) {

                                    val propertiesInStorage = storage.properties
                                    sentPropertiesForSave.forEach{
                                        propertiesInStorage?.put(it.key, it)
                                    }
                                    storage.properties = propertiesInStorage

                                    synchronized(pendingUserProperties){
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

        checkRegistration{ error ->
            error?.let{
                ApphudLog.logE(it.message)
            }?: run{

                val id = updateUser(id = userId)
                this.userId = id
                RequestManager.setParams(this.context, userId, this.deviceId, this.apiKey)

                coroutineScope.launch(errorHandler) {
                    val customer = RequestManager.registrationSync(!didRegisterCustomerAtThisLaunch, is_new, true)
                    customer?.let {
                        launch(Dispatchers.Main) {
                            notifyLoadingCompleted(it)
                        }
                    }
                    error?.let{
                        ApphudLog.logE(it.message)
                    }
                }
            }
        }
    }
    //endregion

    //region === Primary methods ===
    fun getPaywalls() : List<ApphudPaywall>{
        var out: MutableList<ApphudPaywall>
        synchronized(this.paywalls){
            out = this.paywalls.toCollection(mutableListOf())
        }
        return out
    }

    fun permissionGroups(): List<ApphudGroup> {
        var out: MutableList<ApphudGroup>
        synchronized(this.productGroups){
            out = this.productGroups.toCollection(mutableListOf())
        }
        return out
    }

    fun grantPromotional(daysCount: Int, productId: String?, permissionGroup: ApphudGroup?, callback: ((Boolean) -> Unit)?) {
        checkRegistration { error ->
            error?.let {
                callback?.invoke(false)
            } ?: run {
                coroutineScope.launch(errorHandler) {
                    RequestManager.grantPromotional(daysCount, productId, permissionGroup) { customer, error ->
                        launch(Dispatchers.Main) {
                            customer?.let {
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

    fun paywallShown(paywall: ApphudPaywall) {
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

    fun paywallClosed(paywall: ApphudPaywall) {
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

    private fun paywallPaymentCancelled(paywall_id: String?, product_id: String?, error_Code: Int) {
        checkRegistration{ error ->
            error?.let{
                ApphudLog.logI(error.message)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    if (error_Code == BillingClient.BillingResponseCode.USER_CANCELED) {
                        RequestManager.paywallPaymentCancelled(paywall_id, product_id)
                    }else{
                        RequestManager.paywallPaymentError(paywall_id, product_id, error_Code.toString())
                    }
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

    internal fun getSkuDetailsList(): List<SkuDetails> {
        var out: MutableList<SkuDetails>
        synchronized(this.skuDetails){
            out = this.skuDetails.toCollection(mutableListOf())
        }
        return out
    }

    fun sendErrorLogs(message: String) {
        checkRegistration{ error ->
            error?.let{
                ApphudLog.logI(error.message)
            }?: run{
                coroutineScope.launch(errorHandler) {
                    RequestManager.sendErrorLogs(message)
                }
            }
        }
    }
    //endregion

    //region === Secondary methods ===
    internal fun getPackageName(): String{
        return context.packageName
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
        skuDetailsForRestoreIsLoaded_SUBS.set(false)
        skuDetailsForRestoreIsLoaded_INAPP.set(false)
        productsLoaded.set(0)
        customProductsFetchedBlock = null
        storage.clean()
        prevPurchases.clear()
        tempPrevPurchases.clear()
        skuDetails.clear()
        pendingUserProperties.clear()
        allowIdentifyUser = true
        didRegisterCustomerAtThisLaunch = false
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
    //endregion

    //region === Cache ===
    //Groups cache ======================================
    private fun cacheGroups(groups: List<ApphudGroup>) {
        storage.productGroups = groups
    }

    private fun readGroupsFromCache(): MutableList<ApphudGroup>{
        return  storage.productGroups?.toMutableList()?: mutableListOf()
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

    private fun readPaywallsFromCache(): MutableList<ApphudPaywall> {
        return storage.paywalls?.toMutableList()?: mutableListOf()
    }

    private fun updatePaywallsWithSkuDetails(paywalls: List<ApphudPaywall>) {
        synchronized(paywalls) {
            paywalls.forEach { paywall ->
                paywall.products?.forEach { product ->
                    product.skuDetails = getSkuDetailsByProductId(product.product_id)
                }
            }
        }
    }

    //Find SkuDetail  ======================================
    internal fun getSkuDetailsByProductId(productIdentifier: String): SkuDetails? {
        var skuDetail : SkuDetails?
        synchronized(skuDetails){
            skuDetail = skuDetails.let { skuList -> skuList.firstOrNull { it.sku == productIdentifier } }
        }
        return skuDetail
    }

    //endregion
}