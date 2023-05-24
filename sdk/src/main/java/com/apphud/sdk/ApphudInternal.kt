package com.apphud.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.apphud.sdk.body.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.managers.RequestManager.applicationContext
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.gson.GsonBuilder
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
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
    private var productDetails = mutableListOf<ProductDetails>()
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

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val errorHandler = CoroutineExceptionHandler { context, error ->
        error.message?.let { ApphudLog.logE(it) }
    }

    private var customProductsFetchedBlock: ((List<ProductDetails>) -> Unit)? = null

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

    internal fun refreshEntitlements(forceRefresh: Boolean = false){
        if(didRegisterCustomerAtThisLaunch || forceRefresh){
            ApphudLog.log("RefreshEntitlements: didRegister:$didRegisterCustomerAtThisLaunch force:$forceRefresh")
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

                            mainScope.launch {
                                notifyLoadingCompleted(null, productDetails)
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
        synchronized(productDetails) {
            productDetails.clear()
        }

        coroutineScope {
            val subs = async{billing.detailsEx(BillingClient.ProductType.SUBS, ids)}
            val inap =  async{billing.detailsEx(BillingClient.ProductType.INAPP, ids)}

            subs.await()?.let {
                synchronized(productDetails) {
                    productDetails.addAll(it)

                    for(item in productDetails) {
                        ApphudLog.log(item.zza())
                    }
                }
                isSubsLoaded = true
            } ?: run {
                ApphudLog.logE("Unable to load SUBS details", false)
            }

            inap.await()?.let {
                synchronized(productDetails) {
                    productDetails.addAll(it)
                    for(item in productDetails) {
                        ApphudLog.log(item.name  + ":  " + item.toString())
                    }
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
    private fun notifyLoadingCompleted(customerLoaded: Customer? = null, productDetailsLoaded: List<ProductDetails>? = null, fromCache: Boolean = false){
        var restorePaywalls = true

        productDetailsLoaded?.let{
            productGroups = readGroupsFromCache()
            updateGroupsWithProductDetails(productGroups)

            //notify that productDetails are loaded
            apphudListener?.apphudFetchProductDetails(getProductDetailsList())
            customProductsFetchedBlock?.invoke(getProductDetailsList())
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

            if (restorePaywalls) {
                paywalls = readPaywallsFromCache()
            }

            currentUser = it
            userId = it.user.userId

            apphudListener?.apphudNonRenewingPurchasesUpdated(currentUser!!.purchases)
            apphudListener?.apphudSubscriptionsUpdated(currentUser!!.subscriptions)

            if (!didRegisterCustomerAtThisLaunch) {
                apphudListener?.userDidLoad()
            }
            didRegisterCustomerAtThisLaunch = true
        }

        updatePaywallsWithProductDetails(paywalls)

        if(restorePaywalls && currentUser != null && paywalls.isNotEmpty() && productDetails.isNotEmpty() && notifyFullyLoaded){
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
            mutex.withLock {
                if(currentUser == null || forceRegistration) {
                    val threads = listOf(
                        async {
                            customer = RequestManager.registrationSync(
                                !didRegisterCustomerAtThisLaunch,
                                is_new,
                                forceRegistration
                            )
                        }
                    )
                    threads.awaitAll().let {
                        customer?.let {
                            storage.lastRegistration = System.currentTimeMillis()

                            mainScope.launch {
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
                            mainScope.launch {
                                completionHandler?.invoke(
                                    null,
                                    ApphudError("Registration: error")
                                )
                            }
                        }
                    }
                }else{
                    mainScope.launch {
                        completionHandler?.invoke(currentUser, null)
                    }
                }
            }
        }
    }

    private suspend fun repeatRegistrationSilent(){
        RequestManager.registrationSync(!didRegisterCustomerAtThisLaunch, is_new,true)
    }

    internal fun productsFetchCallback(callback: (List<ProductDetails>) -> Unit) {
        customProductsFetchedBlock = callback
        if (productDetails.isNotEmpty()) {
            customProductsFetchedBlock?.invoke(productDetails)
        }
    }
    //endregion

    //region === Purchases ===
    internal fun purchase(
        activity: Activity,
        product: ApphudProduct,
        offerIdToken: String?,
        oldToken: String?,
        replacementMode: Int?,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        var details = product.productDetails
        if(details == null){
            details = getProductDetailsByProductId(product.product_id)
            product.productDetails = details
        }

        details?.let{
            if(details.productType == BillingClient.ProductType.SUBS){
                offerIdToken?.let{
                    purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, callback)
                }?: run{
                    callback?.invoke(ApphudPurchaseResult(null,null,null, ApphudError("offerIdToken required")))
                }
            }else{
                purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, callback)
            }
        }?: run{
            coroutineScope.launch(errorHandler) {
                fetchDetails(activity, product,  offerIdToken, oldToken, replacementMode, callback)
            }
        }
    }

    private suspend fun fetchDetails(
        activity: Activity,
        apphudProduct: ApphudProduct,
        offerIdToken: String?,
        oldToken: String?,
        prorationMode: Int?,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        val productName: String = apphudProduct.product_id
        if(loadDetails(productName, apphudProduct)){
            getProductDetailsByProductId(productName)?.let { details ->
                mainScope.launch {
                    apphudProduct.productDetails = details
                    purchaseInternal(activity, apphudProduct, offerIdToken, oldToken, prorationMode, callback)
                }
            }
        }else{
            val message = "Unable to fetch product with given product id: $productName" + apphudProduct.let { " [Apphud product ID: " + it.id + "]" }
            ApphudLog.log(message = message,sendLogToServer = true)
            mainScope.launch {
                callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
            }
        }
    }

    private suspend fun loadDetails(
        productId: String?,
        apphudProduct: ApphudProduct?) :Boolean
    {
        val productName: String = productId ?: apphudProduct?.product_id!!
        ApphudLog.log("Could not find Product for product id: $productName in memory")
        ApphudLog.log("Now try fetch it from Google Billing")

        return coroutineScope {
            var isInapLoaded = false
            var isSubsLoaded = false

            val subs = async { billing.detailsEx(BillingClient.ProductType.SUBS, listOf(productName)) }
            val inap = async { billing.detailsEx(BillingClient.ProductType.INAPP, listOf(productName)) }

            subs.await()?.let {
                productDetails.addAll(it)
                isSubsLoaded = true

                ApphudLog.log("Google Billing return this info for product id = $productName :")
                it.forEach { ApphudLog.log("$it") }
            } ?: run {
                ApphudLog.logE("Unable to load SUBS details")
            }

            inap.await()?.let {
                productDetails.addAll(it)
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
        apphudProduct: ApphudProduct,
        offerIdToken: String?,
        oldToken: String?,
        replacementMode: Int?,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        billing.acknowledgeCallback = { status, purchase ->
            billing.acknowledgeCallback = null
            mainScope.launch {
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
                        ackPurchase(purchase, apphudProduct, offerIdToken, oldToken, callback)
                    }
                }
            }
        }
        billing.consumeCallback = { status, purchase ->
            billing.consumeCallback = null
            mainScope.launch {
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
                        ackPurchase(purchase, apphudProduct, offerIdToken, oldToken, callback)
                    }
                }
            }
        }
        billing.purchasesCallback = { purchasesResult ->
            billing.purchasesCallback = null
            mainScope.launch {
                when (purchasesResult) {
                    is PurchaseUpdatedCallbackStatus.Error -> {
                        var message =
                            apphudProduct.productDetails?.let{
                                "Unable to buy product with given product id: ${it.productId} "
                            }?: run{
                                paywallPaymentCancelled(apphudProduct.paywall_id, apphudProduct.product_id, purchasesResult.result.responseCode)
                                "Unable to buy product with given product id: ${apphudProduct.productDetails?.productId} "
                            }

                        message += " [Apphud product ID: " + apphudProduct.id + "]"

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

                        val detailsType = apphudProduct.productDetails?.productType ?: run {
                            apphudProduct.productDetails?.productType
                        }

                        purchasesResult.purchases.forEach {
                            when (it.purchaseState) {
                                Purchase.PurchaseState.PURCHASED ->
                                    when (detailsType) {
                                        BillingClient.ProductType.SUBS -> {
                                            if (!it.isAcknowledged) {
                                                billing.acknowledge(it)
                                            }
                                        }
                                        BillingClient.ProductType.INAPP -> {
                                            billing.consume(it)
                                        }
                                        else -> {
                                            val message = "After purchase type is null"
                                            ApphudLog.log(message)
                                            callback?.invoke(ApphudPurchaseResult(null,null, it, ApphudError(message)))
                                        }
                                    }
                                else -> {
                                    val message = "After purchase state: ${it.purchaseState}" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
                                    ApphudLog.log(message = message)
                                    callback?.invoke(ApphudPurchaseResult(null,null, it, ApphudError(message)))
                                }
                            }
                        }
                    }
                }
            }
        }

        apphudProduct.productDetails?.let{
            paywallCheckoutInitiated(apphudProduct.paywall_id, apphudProduct.product_id)
            billing.purchase(activity, it, offerIdToken, oldToken, replacementMode, deviceId)
        }?: run{
            val message = "Unable to buy product with because ProductDetails is null" + apphudProduct?.let{ " [Apphud product ID: " + it.id + "]"}
            ApphudLog.log(message = message)
            mainScope.launch {
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
        apphudProduct: ApphudProduct,
        offerIdToken: String?,
        oldToken: String?,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        coroutineScope.launch(errorHandler) {
            RequestManager.purchased(purchase, apphudProduct, offerIdToken, oldToken) { customer, error ->
                mainScope.launch {
                    customer?.let {
                        val newSubscriptions =
                            customer.subscriptions.firstOrNull { it.productId == purchase.products.first() }

                        val newPurchases =
                            customer.purchases.firstOrNull { it.productId == purchase.products.first() }

                        notifyLoadingCompleted(it)

                        if (newSubscriptions == null && newPurchases == null) {
                            val productId =apphudProduct.productDetails?.let { apphudProduct.productDetails?.productId } ?: purchase.products.first()?:"unknown"
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

    internal fun trackPurchase(purchase: Purchase, productDetails: ProductDetails, offerIdToken: String?, paywallIdentifier: String? = null) {
        ApphudLog.log("TrackPurchase()")
        coroutineScope.launch(errorHandler) {
            sendPurchasesToApphud(
                paywallIdentifier,
                null,
                purchase,
                productDetails,
                offerIdToken,
                null,
                true
            )
        }
    }
    //endregion

    //region === Restore purchases ===
    internal fun restorePurchases(callback: ApphudPurchasesRestoreCallback) {
        syncPurchases(observerMode = false, callback = callback)
    }

    private val mutexSync = Mutex()
    internal fun syncPurchases(
        paywallIdentifier: String? = null,
        observerMode: Boolean = true,
        callback: ApphudPurchasesRestoreCallback? = null
    ) {
        ApphudLog.log("SyncPurchases()")
        checkRegistration { error ->
            error?.let {
                ApphudLog.log("SyncPurchases: checkRegistration fail")
                callback?.invoke(null, null, error)
            } ?: run {
                ApphudLog.log("SyncPurchases: user registered")
                coroutineScope.launch(errorHandler) {
                    mutexSync.withLock {
                        ApphudLog.log("SyncPurchases: mutex Lock")
                        val subsResult = billing.queryPurchaseHistorySync(BillingClient.ProductType.SUBS)
                        val inapsResult = billing.queryPurchaseHistorySync(BillingClient.ProductType.INAPP)

                        var purchases = mutableListOf<PurchaseHistoryRecord>()
                        purchases.addAll(processHistoryCallbackStatus(subsResult))
                        purchases.addAll(processHistoryCallbackStatus(inapsResult))

                        if (purchases.isEmpty()) {
                            ApphudLog.log(
                                message = "Nothing to restore",
                                sendLogToServer = false
                            )
                            mainScope.launch {
                                refreshEntitlements(true)
                                currentUser?.let {
                                    callback?.invoke(it.subscriptions, it.purchases, null)
                                }
                            }
                        } else {
                            ApphudLog.log("Products to restore: $purchases")

                            val restoredPurchases = mutableListOf<PurchaseRecordDetails>()
                            val subsRestored =
                                billing.restoreSync(BillingClient.ProductType.SUBS, purchases)
                            val inapsRestored =
                                billing.restoreSync(BillingClient.ProductType.INAPP, purchases)

                            restoredPurchases.addAll(processRestoreCallbackStatus(subsRestored))
                            restoredPurchases.addAll(processRestoreCallbackStatus(inapsRestored))

                            ApphudLog.log("Products restored: $restoredPurchases")

                            if (observerMode && prevPurchases.containsAll(restoredPurchases)) {
                                ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                                storage.isNeedSync = false
                                mainScope.launch {
                                    refreshEntitlements(true)
                                }
                            } else {
                                ApphudLog.log("SyncPurchases: call syncPurchasesWithApphud()")
                                sendPurchasesToApphud(
                                    paywallIdentifier,
                                    restoredPurchases,
                                    null,
                                    null,
                                    null,
                                    callback,
                                    observerMode
                                )
                            }
                        }
                        ApphudLog.log("SyncPurchases: mutex unlock")
                    }
                }
            }
        }
    }

    private suspend fun sendPurchasesToApphud(
        paywallIdentifier: String? = null,
        tempPurchaseRecordDetails: List<PurchaseRecordDetails>?,
        purchase: Purchase?,
        productDetails: ProductDetails?,
        offerIdToken: String?,
        callback: ApphudPurchasesRestoreCallback? = null,
        observerMode: Boolean
    ){
        val apphudProduct: ApphudProduct? = tempPurchaseRecordDetails?.let {
            findJustPurchasedProduct(paywallIdentifier, it)
        }?: run{
            findJustPurchasedProduct(paywallIdentifier, productDetails)
        }
        val customer = RequestManager.restorePurchasesSync(apphudProduct, tempPurchaseRecordDetails, purchase, productDetails, offerIdToken, observerMode)
        customer?.let{
            tempPurchaseRecordDetails?.let{ records ->
                if(records.isNotEmpty() && (it.subscriptions.size + it.purchases.size) == 0) {
                    val message = "Unable to completely validate all purchases. " +
                            "Ensure Google Service Credentials are correct and have necessary permissions. " +
                            "Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support."
                    ApphudLog.logE(message = message)
                }else{
                    ApphudLog.log("SyncPurchases: customer was successfully updated $customer")
                }

                storage.isNeedSync = false
                prevPurchases.addAll(records)
            }

            userId = customer.user.userId
            storage.updateCustomer(it, apphudListener)

            currentUser = storage.customer
            RequestManager.currentUser = currentUser

            ApphudLog.log("SyncPurchases: customer was updated $customer")
            mainScope.launch {
                apphudListener?.apphudSubscriptionsUpdated(it.subscriptions)
                apphudListener?.apphudNonRenewingPurchasesUpdated(it.purchases)
                callback?.invoke(it.subscriptions, it.purchases, null)
            }
        }?: run{
            val message = "Failed to restore purchases"
            ApphudLog.logE(message = message)
            mainScope.launch {
                callback?.invoke(null, null, ApphudError(message))
            }
        }
    }

    private fun processHistoryCallbackStatus(result: PurchaseHistoryCallbackStatus): List<PurchaseHistoryRecord>{
        when (result){
            is PurchaseHistoryCallbackStatus.Error ->{
                val type = if(result.type() == BillingClient.ProductType.SUBS) "subscriptions" else "in-app products"
                ApphudLog.log("Failed to load history for $type with error: ("
                        + "${result.result?.responseCode})"
                        + "${result.result?.debugMessage})")
            }
            is PurchaseHistoryCallbackStatus.Success ->{
                return result.purchases
            }
        }
        return emptyList()
    }

    private fun processRestoreCallbackStatus(result: PurchaseRestoredCallbackStatus): List<PurchaseRecordDetails>{
        when (result){
            is PurchaseRestoredCallbackStatus.Error ->{
                val type = if(result.type() == BillingClient.ProductType.SUBS) "subscriptions" else "in-app products"
                val error =
                    ApphudError(message = "Restore Purchases is failed for $type",
                        secondErrorMessage = result.message,
                        errorCode = result.result?.responseCode)
                ApphudLog.log(message = error.toString(), sendLogToServer = true)
            }
            is PurchaseRestoredCallbackStatus.Success ->{
                return result.purchases
            }
        }
        return emptyList()
    }

    private fun findJustPurchasedProduct(paywallIdentifier: String?, tempPurchaseRecordDetails: List<PurchaseRecordDetails>): ApphudProduct?{
        try {
            paywallIdentifier?.let {
                getPaywalls().firstOrNull { it.identifier == paywallIdentifier }
                    ?.let { currentPaywall ->
                        val record = tempPurchaseRecordDetails.maxByOrNull { it.record.purchaseTime }
                        record?.let { rec ->
                            val offset = System.currentTimeMillis() - rec.record.purchaseTime
                            if (offset < 300000L) { // 5 min
                                return currentPaywall.products?.find { it.productDetails?.productId == rec.details.productId }
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

    private fun findJustPurchasedProduct(paywallIdentifier: String?, productDetails: ProductDetails?): ApphudProduct?{
        try {
            paywallIdentifier?.let {
                getPaywalls().firstOrNull { it.identifier == paywallIdentifier }
                    ?.let { currentPaywall ->
                        productDetails?.let { details ->
                            return currentPaywall.products?.find { it.productDetails?.productId == details.productId }
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
                            mainScope.launch {
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
                        mainScope.launch {
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
                        mainScope.launch {
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
                        mainScope.launch {
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

    fun subscriptions() :List<ApphudSubscription> {
        var subscriptions : List<ApphudSubscription> = mutableListOf()
        this.currentUser?.let{user ->
            synchronized(user){
                subscriptions = user.subscriptions.toCollection(mutableListOf())
            }
        }
        return subscriptions
    }

    fun purchases() :List<ApphudNonRenewingPurchase> {
        var purchases : List<ApphudNonRenewingPurchase> = mutableListOf()
        this.currentUser?.let{user ->
            synchronized(user){
                purchases = user.purchases.toCollection(mutableListOf())
            }
        }
        return purchases
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

    internal fun getProductDetailsList(): List<ProductDetails> {
        var out: MutableList<ProductDetails>
        synchronized(this.productDetails){
            out = this.productDetails.toCollection(mutableListOf())
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

    private suspend fun fetchAdvertisingId(): String?{
        return RequestManager.fetchAdvertisingId()
    }

    private suspend fun fetchAppSetId() :String? =
        suspendCancellableCoroutine { continuation ->
            val client = AppSet.getClient(applicationContext)
            val task: Task<AppSetIdInfo> = client.appSetIdInfo
            task.addOnSuccessListener{
                // Determine current scope of app set ID.
                val scope: Int = it.scope

                // Read app set ID value, which uses version 4 of the
                // universally unique identifier (UUID) format.
                val id: String = it.id

                if(continuation.isActive) {
                    continuation.resume(id)
                }
            }
            task.addOnFailureListener {
                if(continuation.isActive) {
                    continuation.resume(null)
                }
            }
            task.addOnCanceledListener {
                if(continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }

    private suspend fun fetchAndroidId() :String? =
        suspendCancellableCoroutine { continuation ->
            val androidId: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if(continuation.isActive) {
                continuation.resume(androidId)
            }
        }

    @Synchronized
    fun collectDeviceIdentifiers() {
        if(!isInitialized()) {
            ApphudLog.logE("collectDeviceIdentifiers: $MUST_REGISTER_ERROR")
            return
        }

        if(ApphudUtils.optOutOfTracking) {
            ApphudLog.logE("Unable to collect device identifiers because optOutOfTracking() is called.")
            return
        }

        coroutineScope.launch(errorHandler) {
            var repeatRegistration = false
            val threads = listOf(
                async {
                    val advertisingId = fetchAdvertisingId()
                    advertisingId?.let{
                        if(it == "00000000-0000-0000-0000-000000000000"){
                            ApphudLog.logE("Unable to fetch Advertising ID, please check AD_ID permission in the manifest file.")
                        } else if (RequestManager.advertisingId.isNullOrEmpty() || RequestManager.advertisingId != it) {
                            repeatRegistration = true
                            RequestManager.advertisingId = it
                            ApphudLog.log(message = "advertisingID: $it")
                        }
                    }
                },
                async {
                    val appSetID = fetchAppSetId()
                    appSetID?.let{
                        repeatRegistration = true
                        RequestManager.appSetId = it
                        ApphudLog.log(message = "appSetID: $it")
                    }
                },
                async {
                    val androidID = fetchAndroidId()
                    androidID?.let{
                        repeatRegistration = true
                        RequestManager.androidId = it
                        ApphudLog.log(message = "androidID: $it")
                    }
                }
            )
            threads.awaitAll().let {
                if(repeatRegistration) {
                    mutex.withLock {
                        repeatRegistrationSilent()
                    }
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
        productsLoaded.set(0)
        customProductsFetchedBlock = null
        storage.clean()
        prevPurchases.clear()
        productDetails.clear()
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

    private fun updateGroupsWithProductDetails(productGroups: List<ApphudGroup>) {
        productGroups.forEach { group ->
            group.products?.forEach { product ->
                product.productDetails = getProductDetailsByProductId(product.product_id)
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

    private fun updatePaywallsWithProductDetails(paywalls: List<ApphudPaywall>) {
        synchronized(paywalls) {
            paywalls.forEach { paywall ->
                paywall.products?.forEach { product ->
                    product.productDetails = getProductDetailsByProductId(product.product_id)
                }
            }
        }
    }

    //Find ProductDetails  ======================================
    internal fun getProductDetailsByProductId(productIdentifier: String): ProductDetails? {
        var productDetail : ProductDetails?
        synchronized(productDetails){
            productDetail = productDetails.let { productsList -> productsList.firstOrNull { it.productId == productIdentifier } }
        }
        return productDetail
    }

    //endregion
}