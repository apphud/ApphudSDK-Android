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
import com.apphud.sdk.body.*
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.managers.RequestManager.applicationContext
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

@SuppressLint("StaticFieldLeak")
internal object ApphudInternal {

    //region === Variables ===
    internal val mainScope = CoroutineScope(Dispatchers.Main)
    internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val errorHandler = CoroutineExceptionHandler { context, error ->
        error.message?.let { ApphudLog.logE(it) }
    }

    internal const val ERROR_TIMEOUT = 408
    internal val FALLBACK_ERRORS = listOf(ERROR_TIMEOUT, 500, 502, 503)

    internal lateinit var billing: BillingWrapper
    internal val storage by lazy { SharedPreferencesStorage.getInstance(context) }
    internal var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
    internal var productDetails = mutableListOf<ProductDetails>()
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
    internal var currentUser: Customer? = null
    internal var apphudListener: ApphudListener? = null


    private var customProductsFetchedBlock: ((List<ProductDetails>) -> Unit)? = null
    private var paywallsFetchedBlock: ((List<ApphudPaywall>) -> Unit)? = null
    private var lifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if(fallbackMode){
                    storage.isNeedSync = true
                }
                ApphudLog.log("Application stopped [need sync ${storage.isNeedSync}]")
            }
            Lifecycle.Event.ON_START -> {
                // do nothing
            }
            Lifecycle.Event.ON_CREATE-> {
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)

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
        RequestManager.currentUser = this.currentUser
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

    private var notifyFullyLoaded = false
    @Synchronized
    internal fun notifyLoadingCompleted(customerLoaded: Customer? = null, productDetailsLoaded: List<ProductDetails>? = null, fromCache: Boolean = false, fromFallback: Boolean = false){
        var restorePaywalls = true

        productDetailsLoaded?.let{
            productGroups = readGroupsFromCache()
            updateGroupsWithProductDetails(productGroups)

            //notify that productDetails are loaded
            apphudListener?.apphudFetchProductDetails(getProductDetailsList())
            customProductsFetchedBlock?.invoke(getProductDetailsList())
        }

        customerLoaded?.let {
            if (fromCache || fromFallback) {
                RequestManager.currentUser = it
                notifyFullyLoaded = true
            } else {
                if (it.paywalls.isNotEmpty()) {
                    notifyFullyLoaded = true
                    cachePaywalls(it.paywalls)
                } else {
                    /* Attention:
                     * If customer loaded without paywalls, do not reload paywalls from cache!
                     * If cache time is over, paywall from cache will be NULL
                    */
                    restorePaywalls = false
                }
                storage.updateCustomer(it, apphudListener)
            }

            if (restorePaywalls || fromFallback) {
                paywalls = readPaywallsFromCache()
            }

            currentUser = it
            RequestManager.currentUser = currentUser
            userId = it.user.userId

            apphudListener?.apphudNonRenewingPurchasesUpdated(currentUser!!.purchases)
            apphudListener?.apphudSubscriptionsUpdated(currentUser!!.subscriptions)

            if (!didRegisterCustomerAtThisLaunch) {
                apphudListener?.userDidLoad()
            }
            if (it.isTemporary == false && !fallbackMode){
                didRegisterCustomerAtThisLaunch = true
            }

            if(!fromFallback && fallbackMode){
                disableFallback()
            }
        }

        updatePaywallsWithProductDetails(paywalls)

        if(restorePaywalls && currentUser != null && paywalls.isNotEmpty() && productDetails.isNotEmpty() && notifyFullyLoaded){
            notifyFullyLoaded = false
            apphudListener?.paywallsDidFullyLoad(paywalls)
            paywallsFetchedBlock?.invoke(paywalls)
        }
    }

    private val mutex = Mutex()
    private fun registration(
        userId: UserId,
        deviceId: DeviceId,
        forceRegistration: Boolean = false,
        completionHandler: ((Customer?, ApphudError?) -> Unit)?
    ) {
        coroutineScope.launch(errorHandler) {
            mutex.withLock {
                if(currentUser == null || forceRegistration || currentUser?.isTemporary == true) {
                    ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")
                    ApphudLog.log("Registration conditions: user_is_null=${currentUser == null}, forceRegistration=$forceRegistration isTemporary=${currentUser?.isTemporary}")

                    RequestManager.registration(!didRegisterCustomerAtThisLaunch, is_new, forceRegistration) { customer, error ->
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

                            if(storage.isNeedSync) {
                                coroutineScope.launch(errorHandler) {
                                    ApphudLog.log("Registration: syncPurchases()")
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

    internal fun paywallsFetchCallback(callback: (List<ApphudPaywall>) -> Unit) {
        paywallsFetchedBlock = callback
        if (paywalls.isNotEmpty() && productDetails.isNotEmpty()) {
            paywallsFetchedBlock?.invoke(paywalls)
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

    fun subscriptions() :List<ApphudSubscription> {
        var subscriptions : MutableList<ApphudSubscription> = mutableListOf()
        this.currentUser?.let{user ->
            synchronized(user){
                subscriptions = user.subscriptions.toCollection(mutableListOf())
            }
        }
        return subscriptions.filter { !it.isTemporary || it.isActive()}
    }

    fun purchases() :List<ApphudNonRenewingPurchase> {
        var purchases : MutableList<ApphudNonRenewingPurchase> = mutableListOf()
        this.currentUser?.let{user ->
            synchronized(user){
                purchases = user.purchases.toCollection(mutableListOf())
            }
        }
        return purchases.filter { !it.isTemporary || it.isActive()}
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

    internal fun paywallCheckoutInitiated(paywall_id: String?, product_id: String?) {
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

    internal fun paywallPaymentCancelled(paywall_id: String?, product_id: String?, error_Code: Int) {
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

    internal fun checkRegistration(callback: (ApphudError?) -> Unit){
        if(!isInitialized()) {
            callback.invoke(ApphudError(MUST_REGISTER_ERROR))
            return
        }

        currentUser?.let{
            if(it.isTemporary == false){
                callback.invoke(null)
            } else {
                registration(this.userId, this.deviceId){ _, error ->
                    callback.invoke(error)
                }
            }
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
    internal fun cacheGroups(groups: List<ApphudGroup>) {
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
    internal fun cachePaywalls(paywalls: List<ApphudPaywall>) {
        storage.paywalls = paywalls
    }

    internal fun readPaywallsFromCache(): MutableList<ApphudPaywall> {
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