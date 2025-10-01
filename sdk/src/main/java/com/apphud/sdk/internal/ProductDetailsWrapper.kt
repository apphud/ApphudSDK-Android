package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchaseHistoryRecord
import com.android.billingclient.api.QueryProductDetailsParams
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.ProductId
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.thread
import kotlin.coroutines.resume

internal typealias ProductType = String
internal typealias ApphudProductDetailsCallback = (List<ProductDetails>) -> Unit
internal typealias ApphudProductDetailsRestoreCallback = (PurchaseRestoredCallbackStatus) -> Unit

internal class ProductDetailsWrapper(
    private val billing: BillingClient,
) : BaseAsyncWrapper() {
    var detailsCallback: ApphudProductDetailsCallback? = null
    var restoreCallback: ApphudProductDetailsRestoreCallback? = null

    suspend fun restoreSync(
        @BillingClient.ProductType type: ProductType,
        records: List<PurchaseHistoryRecord>,
    ): PurchaseRestoredCallbackStatus =
        suspendCancellableCoroutine { continuation ->
            val products = records.map { it.products }.flatten().distinct()
            val productList =
                products.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(type)
                        .build()
                }

            val params =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

            thread(start = true, name = "restoreAsync+$type") {
                billing.queryProductDetailsAsync(params) { result, details ->
                    kotlin.runCatching {
                        when (result.isSuccess()) {
                            true -> {
                                val purchases = mutableListOf<PurchaseRecordDetails>()
                                for (productDetails in details) {
                                    val record = records.firstOrNull { it.products.contains(productDetails.productId) }
                                    record?.let {
                                        purchases.add(
                                            PurchaseRecordDetails(
                                                record = it,
                                                details = productDetails,
                                            ),
                                        )
                                    }
                                }

                                when (purchases.isEmpty()) {
                                    true -> {
                                        val message =
                                            "ProductsDetails return empty list for $type and records: $records"
                                        if (continuation.isActive && !continuation.isCompleted) {
                                            continuation.resume(
                                                PurchaseRestoredCallbackStatus.Error(
                                                    type = type,
                                                    result = null,
                                                    message = message
                                                ),
                                            )
                                        }
                                    }
                                    else -> {
                                        if (continuation.isActive && !continuation.isCompleted) {
                                            continuation.resume(
                                                PurchaseRestoredCallbackStatus.Success(
                                                    type = type,
                                                    purchases
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                result.logMessage("RestoreAsync failed for type: $type products: $products")
                                if (continuation.isActive && !continuation.isCompleted) {
                                    continuation.resume(
                                        PurchaseRestoredCallbackStatus.Error(
                                            type = type,
                                            result = result,
                                            message = type
                                        )
                                    )
                                }
                            }
                        }
                    }.onFailure {
                        ApphudLog.logI("Handle repeated call QueryProductDetailsAsync")
                    }
                }
            }
        }

    /**
     * This function will return ProductsDetails according to the requested product list.
     * If manualCallback was defined then the result will be moved to this callback,
     * otherwise detailsCallback will be used
     * */
    fun queryAsync(
        @BillingClient.ProductType type: ProductType,
        products: List<ProductId>,
        manualCallback: ApphudProductDetailsCallback? = null,
    ) {
        val productList =
            products.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(type)
                    .build()
            }

        val params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

        thread(start = true, name = "queryAsync+$type") {
            billing.queryProductDetailsAsync(params) { result, details ->
                when (result.isSuccess()) {
                    true -> {
                        manualCallback?.let { manualCallback.invoke(details) }
                            ?: detailsCallback?.invoke(details)
                    }
                    else -> result.logMessage("Query ProductsDetails Async type: $type products: $products")
                }
            }
        }
    }

    suspend fun querySync(
        @BillingClient.ProductType type: ProductType,
        products: List<ProductId>,
    ): Pair<List<ProductDetails>?, Int> =
        suspendCancellableCoroutine { continuation ->
            val productList =
                products.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(type)
                        .build()
                }

            val params =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

            thread(start = true, name = "queryAsync+$type") {
                billing.queryProductDetailsAsync(params) { result, details ->
                    kotlin.runCatching {
                        when (result.isSuccess()) {
                            true -> {
                                if (continuation.isActive && !continuation.isCompleted) {
                                    continuation.resume(Pair(details, result.responseCode))
                                }
                            }
                            else -> {
                                result.logMessage("Query ProductDetails Async type: $type products: $products")
                                if (continuation.isActive && !continuation.isCompleted) {
                                    continuation.resume(Pair(null, result.responseCode))
                                }
                            }
                        }
                    }.onFailure {
                        ApphudLog.logI("Handle repeated call QueryProductDetails")
                    }
                }
            }
        }

    // Closeable
    override fun close() {
        detailsCallback = null
        restoreCallback = null
    }
}
