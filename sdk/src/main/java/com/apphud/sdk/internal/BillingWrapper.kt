package com.apphud.sdk.internal

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import com.apphud.sdk.handlePurchaseWithoutCallbacks
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import com.apphud.sdk.response
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import kotlin.coroutines.resume

typealias ConsumeCallback = (PurchaseCallbackStatus, Purchase) -> Unit

internal class BillingWrapper(context: Context) : Closeable {
    private val builder =
        BillingClient
            .newBuilder(context)
            .enablePendingPurchases()
    private val purchases = PurchasesUpdated(builder)

    var obfuscatedAccountId: String? = null
    private val billing = builder.build()
    private val prod = ProductDetailsWrapper(billing)
    private val history = HistoryWrapper(billing)

    private val mutex = Mutex()

    private var connectionResponse: Int = BillingClient.BillingResponseCode.OK

    private suspend fun connectIfNeeded(): Boolean {
        var result: Boolean
        mutex.withLock {
            if (billing.isReady) {
                result = true
            } else {
                var retries = 0
                try {
                    val MAX_RETRIES = 5
                    var connected = false
                    while (!connected && retries < MAX_RETRIES) {
                        Thread.sleep(300)
                        retries += 1
                        connected = billing.connect()
                    }
                    result = connected
                } catch (ex: java.lang.Exception) {
                    ApphudLog.log("Connect to Billing failed: ${ex.message ?: "error"}")
                    result = false
                }
            }
        }
        return result
    }

    suspend fun BillingClient.connect(): Boolean {
        var resumed = false
        return suspendCancellableCoroutine { continuation ->
            startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        connectionResponse = billingResult.responseCode
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(true)
                            }
                        } else {
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(false)
                            }
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        connectionResponse = BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                    }
                },
            )
        }
    }

    var purchasesCallback: PurchasesUpdatedCallback? = null
        set(value) {
            field = value
            purchases.callback = value
        }

    suspend fun queryPurchasesSync(): Pair<List<Purchase>?, Int> {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return Pair(null, connectionResponse)
        return history.queryPurchasesSync()
    }

    suspend fun queryPurchaseHistorySync(
        @BillingClient.ProductType type: ProductType,
    ): PurchaseHistoryCallbackStatus {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return PurchaseHistoryCallbackStatus.Error(type, null)
        return history.queryPurchaseHistorySync(type)
    }

    suspend fun detailsEx(
        @BillingClient.ProductType type: ProductType,
        products: List<ProductId>,
    ): Pair<List<ProductDetails>?, Int> {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return Pair(null, connectionResponse)

        return prod.querySync(type = type, products = products)
    }

    suspend fun restoreSync(
        @BillingClient.ProductType type: ProductType,
        products: List<PurchaseHistoryRecord>,
    ): PurchaseRestoredCallbackStatus {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) return PurchaseRestoredCallbackStatus.Error(type)
        return prod.restoreSync(type, products)
    }

    suspend fun purchase(
        activity: Activity,
        details: ProductDetails,
        offerToken: String?,
        oldToken: String?,
        replacementMode: Int?,
        deviceId: String? = null,
    ) {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) {
            ApphudLog.logE("No connection")
            return
        }
        obfuscatedAccountId =
            deviceId?.let {
                val regex = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
                if (regex.matches(input = it)) {
                    it
                } else {
                    null
                }
            }

        try {
            val params: BillingFlowParams =
                if (offerToken != null) {
                    if (oldToken != null) {
                        upDowngradeBillingFlowParamsBuilder(details, offerToken, oldToken, replacementMode)
                    } else {
                        billingFlowParamsBuilder(details, offerToken)
                    }
                } else {
                    billingFlowParamsBuilder(details)
                }
            billing.launchBillingFlow(activity, params)
                .also {
                    when (it.isSuccess()) {
                        true -> {
                            ApphudLog.log("Success response launch Billing Flow")
                        }
                        else -> {
                            val message = "Failed launch Billing Flow"
                            it.logMessage(message)
                        }
                    }
                }
        } catch (ex: Exception) {
            ex.message?.let { ApphudLog.logE(it) }
        }
    }

    suspend fun acknowledge(purchase: Purchase, callBack: AcknowledgeCallback?) {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) {
            callBack?.invoke(PurchaseCallbackStatus.Error("No connection"), purchase)
            return
        }

        val token = purchase.purchaseToken

        if (token.isEmpty() || token.isBlank()) {
            throw IllegalArgumentException("Token empty or blank")
        }

        val params =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
        billing.acknowledgePurchase(params) { result: BillingResult ->
            result.response("purchase acknowledge is failed",
                { callBack?.invoke(PurchaseCallbackStatus.Error(result.responseCode.toString()), purchase) },
                { callBack?.invoke(PurchaseCallbackStatus.Success(), purchase)?: run {
                        ApphudInternal.handlePurchaseWithoutCallbacks(purchase)
                    }
                },
            )
        }
    }

    suspend fun consume(purchase: Purchase, callBack: ConsumeCallback?) {
        val connectIfNeeded = connectIfNeeded()
        if (!connectIfNeeded) {
            callBack?.invoke(PurchaseCallbackStatus.Error("No connection"), purchase)
            return
        }
        val token = purchase.purchaseToken
        val params =
            ConsumeParams.newBuilder()
                .setPurchaseToken(token)
                .build()
        billing.consumeAsync(params) { result, value ->
            result.response(
                message = "failed response with value: $value",
                error = { callBack?.invoke(PurchaseCallbackStatus.Error(value), purchase) },
                success = {
                    callBack?.invoke(PurchaseCallbackStatus.Success(value), purchase) ?: run {
                        ApphudInternal.handlePurchaseWithoutCallbacks(purchase)
                    }
                },
            )
        }
    }

    /**
     * BillingFlowParams Builder for upgrades and downgrades.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param offerToken offer id token
     * @param oldToken the purchase token of the subscription purchase being upgraded or downgraded.
     *
     * @return [BillingFlowParams].
     */
    private fun upDowngradeBillingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String,
        oldToken: String,
        replacementMode: Int?,
    ): BillingFlowParams {
        val pMode = replacementMode ?: BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build(),
            ),
        ).setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldToken)
                .setReplaceProrationMode(
                    pMode,
                )
                .build(),
        )
            .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
            .build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param offerToken  offer id token
     *
     * @return [BillingFlowParams].
     */
    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String,
    ): BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build(),
            ),
        )
            .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
            .build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param productDetails ProductDetails object returned by the library.
     *
     * @return [BillingFlowParams].
     */
    private fun billingFlowParamsBuilder(productDetails: ProductDetails): BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build(),
            ),
        )
            .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
            .build()
    }

    // Closeable
    override fun close() {
        billing.endConnection()
        prod.use { }
        history.use { }
    }
}
