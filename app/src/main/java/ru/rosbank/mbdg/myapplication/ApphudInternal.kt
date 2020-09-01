package ru.rosbank.mbdg.myapplication

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.SkuDetails
import com.google.gson.Gson
import ru.rosbank.mbdg.myapplication.body.*
import ru.rosbank.mbdg.myapplication.client.ApphudClient
import ru.rosbank.mbdg.myapplication.domain.Customer
import ru.rosbank.mbdg.myapplication.domain.PurchaseDetails
import ru.rosbank.mbdg.myapplication.internal.BillingWrapper
import ru.rosbank.mbdg.myapplication.internal.SkuType
import ru.rosbank.mbdg.myapplication.parser.GsonParser
import ru.rosbank.mbdg.myapplication.parser.Parser
import ru.rosbank.mbdg.myapplication.storage.SharedPreferencesStorage
import java.util.*

object ApphudInternal {

    private val parser: Parser = GsonParser(Gson())

    /**
     * @handler use for work with UI-thread. Save to storage, call callbacks
     */
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val client by lazy { ApphudClient(apiKey, parser) }
    private val billing by lazy { BillingWrapper(context) }
    private val storage by lazy { SharedPreferencesStorage(context, parser) }

    internal lateinit var userId: UserId
    internal lateinit var deviceId: DeviceId
    internal lateinit var apiKey: ApiKey
    internal lateinit var context: Context

    internal var currentUser: Customer? = null
    internal var apphudListener: ApphudListener? = null

    internal fun updateUserId(userId: UserId) {
        this.userId = updateUser(id = userId)

        val body = mkRegistrationBody(this.userId, this.deviceId)
        client.registrationUser(body) { customer ->
            handler.post { storage.customer = customer }
        }
    }

    internal fun registration(userId: UserId?, deviceId: DeviceId?) {
        this.userId = updateUser(id = userId)
        this.deviceId = updateDevice(id = deviceId)

        val body = mkRegistrationBody(this.userId, this.deviceId)
        client.registrationUser(body) { customer ->
            handler.post { storage.customer = customer }
        }
// try to continue anyway, because maybe already has cached data, try to fetch products
        fetchProducts()
    }

    internal fun purchase(activity: Activity, details: SkuDetails, callback: (List<Purchase>) -> Unit) {
        billing.acknowledgeCallback = {
            ApphudLog.log("acknowledge success")
        }
        billing.consumeCallback = { value ->
            ApphudLog.log("consume callback value: $value")
        }
        billing.purchasesCallback = { purchases ->
            ApphudLog.log("purchases: $purchases")

            purchases.forEach {
                if (!it.purchase.isAcknowledged) {
                    billing.acknowledge(it.purchase.purchaseToken)
                }
            }
            client.purchased(mkPurchasesBody(purchases)) {
                ApphudLog.log("Response from server after success purchases: $purchases")
            }
            callback.invoke(purchases.map { it.purchase })
        }
        billing.purchase(activity, details)
    }

    internal fun syncPurchases() {
        billing.historyCallback = { purchases ->
            ApphudLog.log("history purchases: $purchases")
            client.purchased(mkPurchaseBody(purchases)) {
                ApphudLog.log("success send history purchases $purchases")
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
            ApphudAttributionProvider.adjust         -> AttributionBody(deviceId, data ?: emptyMap())
            ApphudAttributionProvider.facebook       -> {
                val map = mutableMapOf<String, Any>("fb_device" to true)
                    .also { map -> data?.let { map.putAll(it) } }
                    .toMap()
                AttributionBody(device_id = deviceId, facebook_data = map)
            }
            ApphudAttributionProvider.appsFlyer      -> {
                AttributionBody(
                    device_id = deviceId,
                    appsflyer_id = identifier,
                    appsflyer_data = data ?: emptyMap()
                )
            }
        }

        client.send(body) { attribution ->
            ApphudLog.log("Success without saving send attribution: $attribution")
        }
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
            null -> storage.userId ?: UUID.randomUUID().toString()
            else -> id
        }
        storage.userId = userId

        return userId
    }

    private fun updateDevice(id: DeviceId?): DeviceId {

        val deviceId = when (id) {
            null -> storage.deviceId ?: UUID.randomUUID().toString()
            else -> id
        }
        storage.deviceId = deviceId

        return deviceId
    }

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

    private fun mkPurchaseBody(purchases: List<PurchaseHistoryRecord>) =
        PurchaseBody(
            device_id = deviceId,
            purchases = purchases.map { purchase ->
                PurchaseItemBody(
                    order_id = null,
                    product_id = purchase.sku,
                    purchase_token = purchase.purchaseToken,
                    price_currency_code = null,
                    price_amount_micros = null,
                    subscription_period = null
                )
            }
        )

    private fun mkRegistrationBody(userId: UserId, deviceId: DeviceId) =
        RegistrationBody(
            locale = Locale.getDefault().country,
            sdk_version = BuildConfig.VERSION_NAME,
            app_version = "1.0.0",
            device_family = Build.MANUFACTURER,
            platform = "Android",
            device_type = Build.MODEL,
            os_version = Build.VERSION.RELEASE,
            start_app_version = "1.0",
            idfv = "11112222",
            idfa = "22221111",//TODO взять из девайса
            user_id = userId,
            device_id = deviceId,
            time_zone = TimeZone.getDefault().displayName
        )
}