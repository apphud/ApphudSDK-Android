package com.apphud.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.body.*
import com.apphud.sdk.client.ApphudClient
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.internal.PurchaseCallbackStatus
import com.apphud.sdk.internal.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.apphud.sdk.tasks.advertisingId
import com.google.gson.GsonBuilder
import java.util.*

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
    private val client by lazy { ApphudClient(apiKey, parser) }
    private val billing by lazy { BillingWrapper(context) }
    private val storage by lazy { SharedPreferencesStorage(context, parser) }
    private var generatedUUID = UUID.randomUUID().toString()
    private var prevPurchases = mutableSetOf<PurchaseRecordDetails>()
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

    internal var userId: UserId? = null
    private lateinit var deviceId: DeviceId

    private var is_new = true

    internal lateinit var apiKey: ApiKey
    internal lateinit var context: Context

    internal val currentUser: Customer?
        get() = storage.customer
    internal var apphudListener: ApphudListener? = null

    private val skuDetails = mutableListOf<SkuDetails>()
    /**
     * 0 - we at start point without any skuDetails
     * 1 - we have only one loaded SkuType SUBS or INAPP
     * 2 - we have both loaded SkuType SUBS and INAPP
     * */
    private var skuDetailsIsLoaded = 0

    private var customProductsFetchedBlock: ((List<SkuDetails>) -> Unit)? = null

    private val pendingUserProperties = mutableMapOf<String, ApphudUserProperty>()
    private val userPropertiesRunnable = Runnable { if (isRegistered) updateUserProperties() }

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
                handler.postDelayed(paywallsRunnable, 1000L)
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
        client.registrationUser(body) { customer ->
            handler.post {
                storage.customer = customer
                ApphudLog.log("End updateUserId customer=${customer.toString()}")
            }
        }
    }

    internal fun initialize(
        userId: UserId?,
        deviceId: DeviceId?,
        isFetchProducts: Boolean = true
    ) {
        if (!allowIdentifyUser) {
            ApphudLog.logE("=============================================================" +
                    "\nAbort initializing, because Apphud SDK already initialized." +
                    "\nYou can only call `Apphud.start()` once per app lifecycle." +
                    "\nOr if `Apphud.logout()` was called previously." +
                    "\n=============================================================")
            return
        }
        allowIdentifyUser = false
        ApphudLog.log("try restore cachedPaywalls")
        this.paywalls = cachedPaywalls()
        // try to continue anyway, because maybe already has cached data, try to fetch play market products
        fetchProducts()

        ApphudLog.log("Start initialize with userId=$userId, deviceId=$deviceId")
        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)
        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")
        if (ApphudUtils.adTracking)
            loadAdsId()
        else
            registration(this.userId, this.deviceId)
    }

    private fun fetchProducts() {
        billing.skuCallback = { details ->
            ApphudLog.log("fetchProducts: details from Google Billing: $details")
            skuDetailsIsLoaded++
            if (details.isNotEmpty()) {
                skuDetails.addAll(details)
            }
            if(skuDetailsIsLoaded.isBoothSkuLoaded()) {
                paywalls = cachedPaywalls()
                productGroups = cachedGroups()
                customProductsFetchedBlock?.invoke(skuDetails)
                apphudListener?.apphudFetchSkuDetailsProducts(skuDetails)
            }
        }
        client.allProducts { groups ->
            ApphudLog.log("fetchProducts_V2: products from Apphud server: $groups")
            cacheGroups(groups)
            val ids = groups.map { it -> it.products?.map { it.productId }!! }.flatten()
            billing.details(BillingClient.SkuType.SUBS, ids)
            billing.details(BillingClient.SkuType.INAPP, ids)
        }
    }

    private fun registration(
        userId: UserId?,
        deviceId: DeviceId?
    ) {
        ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId")

        val body = mkRegistrationBody(userId!!, this.deviceId)
        client.registrationUser(body) { customer ->
            isRegistered = true
            handler.post {
                ApphudLog.log("registration: registrationUser customer=${customer.toString()}")
                storage.customer = customer
                apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)

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
        }

        ApphudLog.log("End registration")
    }

    internal fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit) {
        customProductsFetchedBlock = callback
        if (skuDetails.isNotEmpty()) {
            customProductsFetchedBlock?.invoke(skuDetails)
        }
    }

    internal fun purchase(
        activity: Activity,
        productId: String,
        withValidation: Boolean = true,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        val sku = getSkuDetailsByProductId(productId)
        if (sku != null) {
            purchase(activity, sku, null, withValidation, callback)
        } else {
            ApphudLog.log("Could not find SkuDetails for product id: $productId in memory")
            ApphudLog.log("Now try fetch it from Google Billing")
            billing.details(BillingClient.SkuType.SUBS, listOf(productId)) { skuList ->
                ApphudLog.log("Google Billing (SUBS) return this info for product id = $productId :")
                skuList.forEach { ApphudLog.log("$it") }
                skuList.takeIf { it.isNotEmpty() }?.let {
                    skuDetails.addAll(it)
                    purchase(activity, it.first(), null, withValidation, callback)
                } ?: run {
                    val message =
                        "Unable to fetch product (SkuType.SUBS) with given product id: $productId"
                    callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
                }
            }
            billing.details(BillingClient.SkuType.INAPP, listOf(productId)) { skuList ->
                ApphudLog.log("Google Billing (INAPP) return this info for product id = $productId :")
                skuList.forEach { ApphudLog.log("$it") }
                skuList.takeIf { it.isNotEmpty() }?.let {
                    skuDetails.addAll(it)
                    purchase(activity, it.first(), null, withValidation, callback)
                } ?: run {
                    val message =
                        "Unable to fetch product (SkuType.INAPP) with given product id: $productId"
                    callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
                }
            }
        }
    }

    internal fun purchase(
        activity: Activity,
        details: SkuDetails?,
        product: ApphudProduct?,
        withValidation: Boolean,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        billing.acknowledgeCallback = { status, purchase ->
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "After purchase acknowledge is failed with code: ${status.error}"
                    ApphudLog.log(message)
                    callback?.invoke(ApphudPurchaseResult(null,
                        null,
                        purchase,
                        ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("acknowledge success")
                    when {
                        withValidation -> ackPurchase(purchase, details , product, callback)
                        else -> {
                            callback?.invoke(ApphudPurchaseResult(null, null, purchase, null))
                            ackPurchase(purchase, details, product, null)
                        }
                    }
                }
            }
        }
        billing.consumeCallback = { status, purchase ->
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "After purchase consume is failed with value: ${status.error}"
                    ApphudLog.log(message)
                    callback?.invoke(ApphudPurchaseResult(null,
                        null,
                        purchase,
                        ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("consume callback value: ${status.message}")
                    when {
                        withValidation -> ackPurchase(purchase, details,product, callback)
                        else -> {
                            callback?.invoke(ApphudPurchaseResult(null, null, purchase, null))
                            ackPurchase(purchase, details, product, null)
                        }
                    }
                }
            }
        }
        billing.purchasesCallback = { purchasesResult ->
            when (purchasesResult) {
                is PurchaseUpdatedCallbackStatus.Error -> {
                    val message = if(details!=null) {
                        "Unable to buy product with given product id: ${details.sku} "
                    }
                    else {
                        "Unable to buy product with given product id: ${product?.skuDetails?.sku} "
                    }
                    val error =
                        ApphudError(message = message,
                            secondErrorMessage = purchasesResult.result.debugMessage,
                            errorCode = purchasesResult.result.responseCode
                        )
                    callback?.invoke(ApphudPurchaseResult(null, null, null, error))
                }
                is PurchaseUpdatedCallbackStatus.Success -> {
                    ApphudLog.log("purchases: $purchasesResult")
                    val detailsType = if(details!=null) {
                        details.type
                    } else {
                        product?.skuDetails?.type
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
                                val message = "After purchase state: ${it.purchaseState}"
                                ApphudLog.log(message)
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
        if(details!=null) {
            billing.purchase(activity, details)
        } else {
            billing.purchase(activity, product?.skuDetails!!)
        }
    }

    private fun ackPurchase(
        purchase: Purchase,
        details: SkuDetails?,
        product: ApphudProduct?,
        callback: ((ApphudPurchaseResult) -> Unit)?
    ) {
        val purchaseBody = details?.let { makePurchaseBody(purchase, it) }
            ?: product?.let { makePurchaseBodyV2(purchase, it) }
        if (purchaseBody == null) {
            val message =
                "Error!!! SkuDetails and ApphudProduct cannot be null at the same time !!!"
            ApphudLog.logE(message)
            callback?.invoke(ApphudPurchaseResult(null,
                null,
                null,
                ApphudError(message)))
        }
        client.purchased(purchaseBody!!) { customer ->
            handler.post {
                ApphudLog.log("client.purchased: $customer")

                val newSubscriptions = storage.customer?.subscriptions?.let {
                    customer.subscriptions.minus(it)
                } ?: customer.subscriptions

                val newPurchases = storage.customer?.purchases?.let {
                    customer.purchases.minus(it)
                } ?: customer.purchases

                storage.customer = customer

                takeIf { newSubscriptions.isNotEmpty() }?.let {
                    apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                    callback?.invoke(ApphudPurchaseResult(newSubscriptions.first(),
                        null,
                        purchase,
                        null))
                }
                takeIf { newPurchases.isNotEmpty() }?.let {
                    apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                    callback?.invoke(ApphudPurchaseResult(null,
                        newPurchases.first(),
                        purchase,
                        null))
                }
            }
        }
    }

    internal fun restorePurchases(
        callback: (
            subscriptions: List<ApphudSubscription>?,
            purchases: List<ApphudNonRenewingPurchase>?,
            error: ApphudError?
        ) -> Unit
    ) {
        syncPurchases(allowsReceiptRefresh = true, callback = callback)
    }

    internal fun syncPurchases(
        allowsReceiptRefresh: Boolean = false,
        callback: ((
            subscriptions: List<ApphudSubscription>?,
            purchases: List<ApphudNonRenewingPurchase>?,
            error: ApphudError?
        ) -> Unit)? = null
    ) {
        storage.isNeedSync = true
        billing.restoreCallback = { restoreStatus ->
            when (restoreStatus) {
                is PurchaseRestoredCallbackStatus.Error -> {
                    when (restoreStatus.result == null) {
                        //Restore is success, but list of purchases is empty
                        true -> {
                            val error = ApphudError(message = restoreStatus.message ?: "")
                            callback?.invoke(null, null, error)
                        }
                        else -> {
                            val error =
                                ApphudError(message = "Restore Purchases is failed for type: ${restoreStatus.message}",
                                    secondErrorMessage = restoreStatus.result.debugMessage,
                                    errorCode = restoreStatus.result.responseCode)
                            callback?.invoke(null, null, error)
                        }
                    }
                }
                is PurchaseRestoredCallbackStatus.Success -> {
                    ApphudLog.log("PurchaseRestoredCallback: ${restoreStatus.purchases}")
                    if (!allowsReceiptRefresh && prevPurchases.containsAll(restoreStatus.purchases)) {
                        ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                    } else {
                        client.purchased(makeRestorePurchasesBody(restoreStatus.purchases)) { customer ->
                            handler.post {
                                prevPurchases.addAll(restoreStatus.purchases)
                                storage.isNeedSync = false
                                storage.customer = customer
                                ApphudLog.log("SyncPurchases: customer updated $customer")
                                apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                                apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                                callback?.invoke(customer.subscriptions, customer.purchases, null)
                            }
                            ApphudLog.log("SyncPurchases: success send history purchases ${restoreStatus.purchases}")
                        }
                    }
                }
            }

        }
        billing.historyCallback = { purchases ->
            if (!purchases.isNullOrEmpty()) {
                ApphudLog.log("historyCallback: $purchases")
                billing.restore(BillingClient.SkuType.SUBS, purchases)
                billing.restore(BillingClient.SkuType.INAPP, purchases)
            }
        }
        billing.queryPurchaseHistory(BillingClient.SkuType.SUBS)
        billing.queryPurchaseHistory(BillingClient.SkuType.INAPP)
    }

    internal fun addAttribution(
        provider: ApphudAttributionProvider,
        data: Map<String, Any>? = null,
        identifier: String? = null
    ) {
        val body = when (provider) {
            ApphudAttributionProvider.adjust -> AttributionBody(
                deviceId,
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
        }

        if (provider == ApphudAttributionProvider.appsFlyer) {
            val temporary = storage.appsflyer
            when {
                temporary == null -> Unit
                temporary.id == body?.appsflyer_id -> return
                temporary.data == body?.appsflyer_data -> return
            }
        } else if (provider == ApphudAttributionProvider.facebook) {
            val temporary = storage.facebook
            when {
                temporary == null -> Unit
                temporary.data == body?.facebook_data -> return
            }
        }

        ApphudLog.log("before start attribution request: $body")
        body?.let {
            client.send(body) { attribution ->
                ApphudLog.log("Success without saving send attribution: $attribution")
                handler.post {
                    if (provider == ApphudAttributionProvider.appsFlyer) {
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
                    } else if (provider == ApphudAttributionProvider.facebook) {
                        val temporary = storage.facebook
                        storage.facebook = when {
                            temporary == null -> FacebookInfo(body.facebook_data)
                            temporary.data != body.facebook_data -> FacebookInfo(body.facebook_data)
                            else -> temporary
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
            ApphudLog.logE("For key '${key.key}' invalid property type: '$type' for 'value'. Must be one of: [Int, Float, Double, Boolean, String or null]")
            return
        }
        if (increment && !(typeString == "integer" || typeString == "float")) {
            val type = value?.let { value::class.java.name } ?: "unknown"
            ApphudLog.logE("For key '${key.key}' invalid increment property type: '$type' for 'value'. Must be one of: [Int, Float or Double]")
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
        client.userProperties(body) { userproperties ->
            handler.post {
                if (userproperties.success) {
                    pendingUserProperties.clear()
                    ApphudLog.log("User Properties successfully updated.")
                } else {
                    ApphudLog.logE("User Properties update failed with errors")
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
        skuDetailsIsLoaded = 0
        paywallsDelayedCallback = null
        isRegistered = false
        storage.customer = null
        storage.userId = null
        storage.deviceId = null
        userId = null
        generatedUUID = UUID.randomUUID().toString()
        prevPurchases.clear()
        skuDetails.clear()
        allowIdentifyUser = true
        customProductsFetchedBlock = null
        pendingUserProperties.clear()
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

    private fun makePurchaseBody(purchase: Purchase, details: SkuDetails?) =
        PurchaseBody(
            device_id = deviceId,
            purchases = listOf(
                PurchaseItemBody(
                    order_id = purchase.orderId,
                    product_id = purchase.sku,
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = details?.priceCurrencyCode,
                    price_amount_micros = details?.priceAmountMicros,
                    subscription_period = details?.subscriptionPeriod,
                    paywallId = null,
                    product_bundle_id = null
                )
            )
        )

    private fun makePurchaseBodyV2(purchase: Purchase, product: ApphudProduct) =
        PurchaseBody(
            device_id = deviceId,
            purchases = listOf(
                PurchaseItemBody(
                    order_id = purchase.orderId,
                    product_id = purchase.sku,
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = product.skuDetails?.priceCurrencyCode,
                    price_amount_micros = product.skuDetails?.priceAmountMicros,
                    subscription_period = product.skuDetails?.subscriptionPeriod,
                    paywallId = product.paywallId,
                    product_bundle_id = product.productId
                )
            )
        )

    private fun makeRestorePurchasesBody(purchases: List<PurchaseRecordDetails>) =
        PurchaseBody(
            device_id = deviceId,
            purchases = purchases.map { purchase ->
                PurchaseItemBody(
                    order_id = null,
                    product_id = purchase.record.sku,
                    purchase_token = purchase.record.purchaseToken,
                    price_currency_code = purchase.details.priceCurrencyCode,
                    price_amount_micros = purchase.details.priceAmountMicros,
                    subscription_period = purchase.details.subscriptionPeriod,
                    paywallId = null,
                    product_bundle_id = null
                )
            }
        )

    private fun mkRegistrationBody(userId: UserId, deviceId: DeviceId) =
        RegistrationBody(
            locale = Locale.getDefault().formatString(),
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
            is_new = this.is_new
        )

    internal fun getSkuDetailsList(): MutableList<SkuDetails>? {
        return skuDetails.takeIf { skuDetails.isNotEmpty() }
    }

    internal fun getSkuDetailsByProductId(productIdentifier: String): SkuDetails? {
        return getSkuDetailsList()?.let { skuList -> skuList.firstOrNull { it.sku == productIdentifier } }
    }

    private fun tryInvokePaywallsDelayedCallback(){
        if (!paywalls.isNullOrEmpty() && skuDetailsIsLoaded.isBoothSkuLoaded()) {
            setNeedsToUpdatePaywalls = false
            paywallsDelayedCallback?.invoke(paywalls, null)
            paywallsDelayedCallback = null
        }
    }

    internal fun getPaywalls(callback: PaywallCallback) {
        ApphudLog.log("Invoke getPaywalls")
        setNeedsToUpdatePaywalls = false
        fetchPaywallsIfNeeded { paywalls, error, writeToCache ->

            paywalls?.let {
                if (it.isNotEmpty() && writeToCache) {
                    cachePaywalls(paywalls = paywalls)
                }

                updatePaywallsWithSkuDetails(paywalls)

                this.paywalls.apply {
                    clear()
                    addAll(paywalls)
                }
                if(skuDetailsIsLoaded.isBoothSkuLoaded()) {
                    callback.invoke(paywalls, null)
                } else {
                    paywallsDelayedCallback = callback
                    setNeedsToUpdatePaywalls = true
                }
            } ?: run {
                callback.invoke(null, error)
            }
        }
    }

    private fun fetchPaywallsIfNeeded(
        forceRefresh: Boolean = false,
        callback: (paywalls: List<ApphudPaywall>?, error: ApphudError?, writeToCache: Boolean) -> Unit
    ) {
        ApphudLog.log("try fetchPaywallsIfNeeded")

        if (!this.paywalls.isNullOrEmpty() || forceRefresh) {
            ApphudLog.log("Using cached paywalls")
            callback(mutableListOf(*this.paywalls.toTypedArray()), null, false)
            return
        }

        client.paywalls { paywalls, errors ->
            callback.invoke(paywalls, errors, true)
        }
    }

    private fun updatePaywallsWithSkuDetails(paywalls: List<ApphudPaywall>) {
        paywalls.forEach { paywall ->
            paywall.products?.forEach { product ->
                product.skuDetails = getSkuDetailsByProductId(product.productId)
            }
        }
    }

    private fun updateGroupsWithSkuDetails(productGroups: List<ApphudGroup>) {
        productGroups.forEach { group ->
            group.products?.forEach { product ->
                product.skuDetails = getSkuDetailsByProductId(product.productId)
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

}