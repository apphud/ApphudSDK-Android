package ru.rosbank.mbdg.myapplication

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.google.gson.Gson
import ru.rosbank.mbdg.myapplication.body.PurchaseBody
import ru.rosbank.mbdg.myapplication.body.RegistrationBody
import ru.rosbank.mbdg.myapplication.client.ApphudClient
import ru.rosbank.mbdg.myapplication.domain.Customer
import ru.rosbank.mbdg.myapplication.internal.BillingWrapper
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

            purchases.forEach { purchase ->
                if (!purchase.isAcknowledged) {
                    billing.acknowledge(purchase.purchaseToken)
                }
                client.purchased(mkPurchaseBody(details, purchase)) {
                    ApphudLog.log("Response from server after success purchase: $purchase")
                }
            }
            callback.invoke(purchases)
        }
        billing.purchase(activity, details)
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

    private fun mkPurchaseBody(details: SkuDetails, purchase: Purchase) =
        PurchaseBody(
            order_id = purchase.orderId,
            device_id = deviceId,
            product_id = purchase.sku,
            purchase_token = purchase.purchaseToken,
            price_currency_code = details.priceCurrencyCode,
            price_amount_micros = details.priceAmountMicros,
            subscription_period = details.subscriptionPeriod
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
            time_zone = "UTF"
        )
}