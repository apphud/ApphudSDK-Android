package com.apphud.sdk.internal

import com.android.billingclient.api.*
import com.apphud.sdk.*
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.thread
import kotlin.coroutines.resume

typealias ProductType = String
typealias ApphudProductDetailsCallback = (List<ProductDetails>) -> Unit
typealias ApphudProductDetailsRestoreCallback = (PurchaseRestoredCallbackStatus) -> Unit

internal class ProductDetailsWrapper(
    private val billing: BillingClient,
) : BaseAsyncWrapper() {
    var detailsCallback: ApphudProductDetailsCallback? = null
    var restoreCallback: ApphudProductDetailsRestoreCallback? = null

    fun restoreAsync(
        @BillingClient.ProductType type: ProductType,
        recordsToResore: List<PurchaseHistoryRecord>?,
    ) {
        recordsToResore?.let {
            val products = recordsToResore.map { it.products }.flatten()
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
                    when (result.isSuccess()) {
                        true -> {
                            val values = details ?: emptyList()

                            val purchases = mutableListOf<PurchaseRecordDetails>()
                            for (productDetails in values) {
                                val record = recordsToResore.firstOrNull { it.products.contains(productDetails.productId) }
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
                                    val message = "ProductsDetails return empty list for $type and records: $recordsToResore"
                                    ApphudLog.log(message)
                                    restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(type = type, result = null, message = message))
                                }
                                else -> {
                                    restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Success(type = type, purchases))
                                }
                            }
                        }
                        else -> {
                            result.logMessage("RestoreAsync failed for type: $type products: $products")
                            restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(type = type, result = result, message = type.toString()))
                        }
                    }
                }
            }
        } ?: run {
            val message = "List of records to restore is NULL"
            restoreCallback?.invoke(PurchaseRestoredCallbackStatus.Error(type = type, result = null, message = message))
        }
    }

    suspend fun restoreSync(
        @BillingClient.ProductType type: ProductType,
        records: List<PurchaseHistoryRecord>,
    ): PurchaseRestoredCallbackStatus =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
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
                    when (result.isSuccess()) {
                        true -> {
                            val values = details ?: emptyList()

                            val purchases = mutableListOf<PurchaseRecordDetails>()
                            for (productDetails in values) {
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
                                    val message = "ProductsDetails return empty list for $type and records: $records"
                                    ApphudLog.log(message)
                                    if (continuation.isActive && !resumed) {
                                        resumed = true
                                        continuation.resume(
                                            PurchaseRestoredCallbackStatus.Error(type = type, result = null, message = message),
                                        )
                                    }
                                }
                                else -> {
                                    if (continuation.isActive && !resumed) {
                                        resumed = true
                                        continuation.resume(PurchaseRestoredCallbackStatus.Success(type = type, purchases))
                                    }
                                }
                            }
                        }
                        else -> {
                            result.logMessage("RestoreAsync failed for type: $type products: $products")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(PurchaseRestoredCallbackStatus.Error(type = type, result = result, message = type))
                            }
                        }
                    }
                }
            }
        }

    /**
     * This function will return ProductsDetails according to the requested product list.
     * If manualCallback was defined then the result will be moved to this callback, otherwise detailsCallback will be used
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
    ): List<ProductDetails>? =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
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
                            ApphudLog.logI("Query ProductDetails success $type")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(details.orEmpty())
                            }
                        }
                        else -> {
                            result.logMessage("Query ProductDetails Async type: $type products: $products")
                            if (continuation.isActive && !resumed) {
                                resumed = true
                                continuation.resume(null)
                            }
                        }
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
