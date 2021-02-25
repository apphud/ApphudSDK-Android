package com.apphud.sdk

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.body.AttributionBody
import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.body.PurchaseItemBody
import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.client.ApphudClient
import com.apphud.sdk.domain.*
import com.apphud.sdk.internal.BillingWrapper
import com.apphud.sdk.parser.GsonParser
import com.apphud.sdk.parser.Parser
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.apphud.sdk.tasks.advertisingId
import com.google.gson.GsonBuilder
import java.util.*

internal object ApphudInternal {

    private val builder = GsonBuilder()
        .setPrettyPrinting()
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

    private var advertisingId: String? = null
        get() = storage.advertisingId
        set(value) {
            field = value
            if (storage.advertisingId != value) {
                storage.advertisingId = value
                ApphudLog.log("advertisingId = $advertisingId is fetched and saved")
            }
            ApphudLog.log("advertisingId: continue registration")
            updateRegistration()
        }

    private var allowIdentifyUser = true

    internal var userId: UserId? = null
    private lateinit var deviceId: DeviceId

    private var is_new = true

    internal lateinit var apiKey: ApiKey
    internal lateinit var context: Context

    internal val currentUser: Customer?
        get() = storage.customer
    internal var apphudListener: ApphudListener? = null

    private val skuDetails = mutableListOf<SkuDetails>()

    private var customProductsFetchedBlock : ((List<SkuDetails>) -> Unit)? = null

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
        ApphudLog.log("Start updateUserId userId=$userId" )
        val id = updateUser(id = userId)
        this.userId = id

        val body = mkRegistrationBody(id, deviceId)
        client.registrationUser(body) { customer ->
            handler.post {
                storage.customer = customer
                ApphudLog.log("End updateUserId customer=${customer.toString()}" )
            }
        }
    }

    internal fun initialize(
        userId: UserId?,
        deviceId: DeviceId?,
        isFetchProducts: Boolean = true
    ){
        if(!allowIdentifyUser){
            ApphudLog.log("=============================================================" +
                          "\nAbort initializing, because Apphud SDK already initialized." +
                          "\nYou can only call `Apphud.start()` once per app lifecycle."  +
                          "\nOr if `Apphud.logout()` was called previously." +
                          "\n=============================================================")
            return
        }
        allowIdentifyUser = false
        // try to continue anyway, because maybe already has cached data, try to fetch play market products
        fetchProducts()

        ApphudLog.log("Start initialize with userId=$userId, deviceId=$deviceId" )
        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)
        ApphudLog.log("Start initialize with saved userId=${this.userId}, saved deviceId=${this.deviceId}")
        if (ApphudUtils.adTracking)
            loadAdsId()
        else
            registration(this.userId, this.deviceId)
    }

    private fun registration(
        userId: UserId?,
        deviceId: DeviceId?
    ) {
        ApphudLog.log("Start registration userId=$userId, deviceId=$deviceId" )

        val body = mkRegistrationBody(userId!!, this.deviceId)
        client.registrationUser(body) { customer ->
            handler.post {
                ApphudLog.log("registration registrationUser customer=${customer.toString()}" )
                storage.customer = customer
                apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)

                // try to resend purchases, if prev requests was fail
                if (storage.isNeedSync) {
                    ApphudLog.log("registration syncPurchases" )
                    syncPurchases()
                }
            }
        }

        ApphudLog.log("End registration" )
    }

    internal fun productsFetchCallback(callback: (List<SkuDetails>) -> Unit){
        customProductsFetchedBlock = callback
        if(skuDetails.isNotEmpty()) {
            customProductsFetchedBlock?.invoke(skuDetails)
        }
    }

    internal fun purchase(
            activity: Activity,
            productId: String,
            callback: (List<Purchase>) -> Unit
    ) {
        val sku = getSkuDetailsByProductId(productId)
        if (sku != null) {
            purchase(activity, sku, callback)
        } else {
            ApphudLog.log("Could not find SkuDetails for product id: $productId in memory")
            ApphudLog.log("Now try fetch it from Google Billing")
            billing.details(BillingClient.SkuType.SUBS, listOf(productId)) { skuList ->
                ApphudLog.log("Google Billing (SUBS) return this info for product id = $productId :")
                skuList.forEach { ApphudLog.log("$it") }
                skuList.takeIf { it.isNotEmpty() }?.let { skuDetails.addAll(it); purchase(activity, it.first() , callback) }
            }
            billing.details(BillingClient.SkuType.INAPP, listOf(productId)) { skuList ->
                ApphudLog.log("Google Billing (SUBS) return this info for product id = $productId :")
                skuList.forEach { ApphudLog.log("$it") }
                skuList.takeIf { it.isNotEmpty() }?.let { skuDetails.addAll(it); purchase(activity, it.first() , callback) }
            }
        }
    }

    internal fun purchase(
        activity: Activity,
        details: SkuDetails,
        callback: (List<Purchase>) -> Unit
    ) {
        billing.acknowledgeCallback = {
            ApphudLog.log("acknowledge success")
        }
        billing.consumeCallback = { value ->
            ApphudLog.log("consume callback value: $value")
        }
        billing.purchasesCallback = { purchases ->
            ApphudLog.log("purchases: $purchases")

            client.purchased(mkPurchasesBody(purchases)) { customer ->
                handler.post {
                    apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                    apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                }
            }

            callback.invoke(purchases.map { it.purchase })

            purchases.forEach {

                when (it.purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> when (it.details?.type) {
                        BillingClient.SkuType.SUBS  -> if (!it.purchase.isAcknowledged) {
                            billing.acknowledge(it.purchase.purchaseToken)
                        }
                        BillingClient.SkuType.INAPP -> billing.consume(it.purchase.purchaseToken)
                        else                        -> ApphudLog.log("After purchase type is null")
                    }
                    else                             -> ApphudLog.log("After purchase state: ${it.purchase.purchaseState}")
                }
            }
        }
        billing.purchase(activity, details)
    }

    internal fun syncPurchases() {
        storage.isNeedSync = true
        billing.restoreCallback = { records ->

            ApphudLog.log("syncPurchases: $records")

            when {
                prevPurchases.containsAll(records) -> ApphudLog.log("syncPurchases: Don't send equal purchases from prev state")
                else                               -> client.purchased(mkPurchaseBody(records)) { customer ->
                    handler.post {
                        prevPurchases.addAll(records)
                        storage.isNeedSync = false
                        apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                        apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                    }
                    ApphudLog.log("syncPurchases: success send history purchases $records")
                }
            }
        }
        billing.historyCallback = { purchases ->
            ApphudLog.log("syncPurchases: history purchases: $purchases")
            billing.restore(BillingClient.SkuType.SUBS, purchases)
            billing.restore(BillingClient.SkuType.INAPP, purchases)
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
                temporary == null                      -> Unit
                temporary.id == body?.appsflyer_id     -> return
                temporary.data == body?.appsflyer_data -> return
            }
        } else if (provider == ApphudAttributionProvider.facebook) {
            val temporary = storage.facebook
            when {
                temporary == null                     -> Unit
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
                            temporary == null                     -> AppsflyerInfo(
                                id = body.appsflyer_id,
                                data = body.appsflyer_data
                            )
                            temporary.id != body.appsflyer_id     -> AppsflyerInfo(
                                id = body.appsflyer_id,
                                data = body.appsflyer_data
                            )
                            temporary.data != body.appsflyer_data -> AppsflyerInfo(
                                id = body.appsflyer_id,
                                data = body.appsflyer_data
                            )
                            else                                  -> temporary
                        }
                    } else if (provider == ApphudAttributionProvider.facebook) {
                        val temporary = storage.facebook
                        storage.facebook = when {
                            temporary == null                    -> FacebookInfo(body.facebook_data)
                            temporary.data != body.facebook_data -> FacebookInfo(body.facebook_data)
                            else                                 -> temporary
                        }
                    }
                }
            }
        }
    }

    internal fun logout() {
        clear()
    }

    private fun clear() {
        storage.customer = null
        storage.userId = null
        storage.deviceId = null
        userId = null
        generatedUUID = UUID.randomUUID().toString()
        prevPurchases.clear()
        skuDetails.clear()
        allowIdentifyUser = true
        customProductsFetchedBlock = null
    }

    private fun fetchProducts() {
        billing.skuCallback = { details ->
            ApphudLog.log("fetchProducts: details from Google Billing: $details")
            if (details.isNotEmpty()) {
                skuDetails.addAll(details)
                customProductsFetchedBlock?.invoke(skuDetails)
                apphudListener?.apphudFetchSkuDetailsProducts(details)
            }
        }
        client.allProducts { products ->
            ApphudLog.log("fetchProducts: products from Apphud server: $products")
            val ids = products.map { it.productId }
            billing.details(BillingClient.SkuType.SUBS, ids)
            billing.details(BillingClient.SkuType.INAPP, ids)
        }
    }

    private fun updateUser(id: UserId?): UserId {

        val userId = when (id) {
            null -> storage.userId ?: generatedUUID
            else -> id
        }
        storage.userId = userId
        return userId
    }

    private fun updateDevice(id: DeviceId?): DeviceId {

        val deviceId = when (id) {
            null -> storage.deviceId?.let { is_new = false; it } ?: generatedUUID
            else -> id
        }
        storage.deviceId = deviceId
        return deviceId
    }

    private fun updateRegistration() = registration(userId, deviceId)

    private fun mkPurchasesBody(purchases: List<PurchaseDetails>) =
        PurchaseBody(
            device_id = deviceId,
            purchases = purchases.map {
                PurchaseItemBody(
                    order_id = it.purchase.orderId,
                    product_id = it.purchase.sku,
                    purchase_token = it.purchase.purchaseToken,
                    price_currency_code = it.details?.priceCurrencyCode,
                    price_amount_micros = it.details?.priceAmountMicros,
                    subscription_period = it.details?.subscriptionPeriod
                )
            }
        )

    private fun mkPurchaseBody(purchases: List<PurchaseRecordDetails>) =
        PurchaseBody(
            device_id = deviceId,
            purchases = purchases.map { purchase ->
                PurchaseItemBody(
                    order_id = null,
                    product_id = purchase.record.sku,
                    purchase_token = purchase.record.purchaseToken,
                    price_currency_code = purchase.details.priceCurrencyCode,
                    price_amount_micros = purchase.details.priceAmountMicros,
                    subscription_period = purchase.details.subscriptionPeriod
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
            is_sandbox = isDebuggable(context),
            is_new = this.is_new
        )

    internal fun getSkuDetailsList(): MutableList<SkuDetails>? {
        return skuDetails.takeIf { skuDetails.isNotEmpty() }
    }

    internal fun getSkuDetailsByProductId(productIdentifier: String): SkuDetails? {
        return getSkuDetailsList()?.let { skuList -> skuList.firstOrNull { it.sku == productIdentifier } }
    }

    private fun isDebuggable(ctx: Context): Boolean {
        return 0 != ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
    }
}