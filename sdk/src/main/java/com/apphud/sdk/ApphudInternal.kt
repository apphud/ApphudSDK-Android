package com.apphud.sdk

import android.app.Activity
import android.content.Context
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
import com.apphud.sdk.domain.Customer
import com.apphud.sdk.domain.PurchaseDetails
import com.apphud.sdk.domain.PurchaseRecordDetails
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
    private var appsflyerBody: AttributionBody? = null

    /**
     * @handler use for work with UI-thread. Save to storage, call callbacks
     */
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val client by lazy { ApphudClient(apiKey, parser) }
    private val billing by lazy { BillingWrapper(context) }
    private val storage by lazy { SharedPreferencesStorage(context, parser) }
    private var generatedUUID = UUID.randomUUID().toString()

    private var advertisingId: String? = null
        get() = storage.advertisingId
        set(value) {
            field = value
            if (storage.advertisingId != value) {
                storage.advertisingId = value
                updateRegistration()
            }
        }

    internal var userId: UserId? = null
    private lateinit var deviceId: DeviceId

    internal lateinit var apiKey: ApiKey
    internal lateinit var context: Context

    internal val currentUser: Customer?
        get() = storage.customer
    internal var apphudListener: ApphudListener? = null

    internal fun loadAdsId() {
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
        val id = updateUser(id = userId)
        this.userId = id

        val body = mkRegistrationBody(id, deviceId)
        client.registrationUser(body) { customer ->
            handler.post { storage.customer = customer }
        }
    }

    internal fun registration(
        userId: UserId?,
        deviceId: DeviceId?,
        isFetchProducts: Boolean = true
    ) {
        val id = updateUser(id = userId)
        this.userId = id
        this.deviceId = updateDevice(id = deviceId)

        val body = mkRegistrationBody(id, this.deviceId)
        client.registrationUser(body) { customer ->
            handler.post {
                storage.customer = customer
                apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
            }
        }
        if (isFetchProducts) {
            // try to continue anyway, because maybe already has cached data, try to fetch products
            fetchProducts()
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
                ApphudLog.log("Response from server after success purchases: $purchases")
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
        billing.restoreCallback = { records ->
            client.purchased(mkPurchaseBody(records)) { customer ->
                handler.post {
                    apphudListener?.apphudSubscriptionsUpdated(customer.subscriptions)
                    apphudListener?.apphudNonRenewingPurchasesUpdated(customer.purchases)
                }
                ApphudLog.log("success send history purchases $records")
            }
        }
        billing.historyCallback = { purchases ->
            ApphudLog.log("history purchases: $purchases")
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
            ApphudAttributionProvider.adjust    -> AttributionBody(
                deviceId,
                adid = identifier,
                adjust_data = data ?: emptyMap()
            )
            ApphudAttributionProvider.facebook  -> {
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
            val temporary = appsflyerBody
            appsflyerBody = when {
                temporary == null                                -> body
                temporary.appsflyer_id != body?.appsflyer_id     -> body
                temporary.appsflyer_data != body?.appsflyer_data -> body
                else                                             -> return
            }
        }

        ApphudLog.log("before start attribution request: $body")
        body?.let {
            client.send(body) { attribution ->
                ApphudLog.log("Success without saving send attribution: $attribution")
            }
        }
    }

    internal fun logout() {
        clear()
    }

    private fun clear() {
        storage.advertisingId = null
        storage.customer = null
        storage.userId = null
        storage.deviceId = null
        userId = null
        generatedUUID = UUID.randomUUID().toString()
    }

    private fun fetchProducts() {
        billing.skuCallback = { details ->
            ApphudLog.log("details: $details")
            apphudListener?.apphudFetchSkuDetailsProducts(details)
        }
        client.allProducts { products ->
            ApphudLog.log("products: $products")
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
            null -> storage.deviceId ?: generatedUUID
            else -> id
        }
        storage.deviceId = deviceId
        return deviceId
    }

    private fun updateRegistration() =
        registration(userId, deviceId, isFetchProducts = false)

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
            is_sandbox = BuildConfig.DEBUG
        )
}