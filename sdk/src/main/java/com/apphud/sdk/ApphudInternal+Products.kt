package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal var productsStatus = ApphudProductsStatus.none
internal var productsResponseCode = BillingClient.BillingResponseCode.OK
private val mutexProducts = Mutex()

internal enum class ApphudProductsStatus {
    none,
    loading,
    finished,
}

internal fun ApphudInternal.finishedLoadingProducts(): Boolean {
    return productsStatus == ApphudProductsStatus.finished
}

internal fun ApphudInternal.shouldLoadProducts(): Boolean {
    return productsStatus == ApphudProductsStatus.none || (productsStatus == ApphudProductsStatus.finished && productDetails.isEmpty())
}

internal fun ApphudInternal.loadProducts() {
    if (!shouldLoadProducts()) { return }
    // continue only if status is none or finished with empty products

    if (productsStatus != ApphudProductsStatus.loading) {
        productsStatus = ApphudProductsStatus.loading
    }

    coroutineScope.launch(errorHandler) {
        mutexProducts.withLock {
            async {
                val result = fetchProducts()
                productsResponseCode = result
                productsStatus = ApphudProductsStatus.finished

                mainScope.launch {
                    notifyLoadingCompleted(null, productDetails, false, false)
                }
            }
        }
    }
}

private suspend fun ApphudInternal.fetchProducts(): Int {
    val permissionGroupsCopy = getPermissionGroups()
    if (permissionGroupsCopy.isEmpty() || storage.needUpdateProductGroups()) {
        val groupsList = RequestManager.allProducts()
        groupsList?.let { groups ->
            cacheGroups(groups)
            val ids = groups.map { it -> it.products?.map { it.productId }!! }.flatten()
            return fetchDetails(ids)
        }
    } else {
        val ids = permissionGroupsCopy.map { it -> it.products?.map { it.productId }!! }.flatten()
        return fetchDetails(ids)
    }
    return BillingClient.BillingResponseCode.OK
}

internal suspend fun ApphudInternal.fetchDetails(ids: List<String>): Int {
    // Assuming ProductDetails has a property 'id' that corresponds to the product ID
    val existingIds = synchronized(productDetails) { productDetails.map { it.productId } }

    val idsToFetch = ids.filterNot { existingIds.contains(it) }

    // If all IDs are already loaded, return immediately
    if (idsToFetch.isEmpty()) {
        return BillingClient.BillingResponseCode.OK
    }

    ApphudLog.log("Fetching Product Details: ${idsToFetch.toString()}")

    if (productsStatus != ApphudProductsStatus.loading) {
        productsStatus = ApphudProductsStatus.loading
    }

    var responseCode = BillingClient.BillingResponseCode.OK

    coroutineScope {
        val subsResult = async { billing.detailsEx(BillingClient.ProductType.SUBS, idsToFetch) }.await()
        val inAppResult = async { billing.detailsEx(BillingClient.ProductType.INAPP, idsToFetch) }.await()

        subsResult.first?.let { subsDetails ->
            synchronized(productDetails) {
                // Add new subscription details if they're not already present
                subsDetails.forEach { detail ->
                    if (!productDetails.map { it.productId }.contains(detail.productId)) {
                        productDetails.add(detail)
                    }
                }
            }
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details", false)
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = subsResult.second
            }
        }

        inAppResult.first?.let { inAppDetails ->
            synchronized(productDetails) {
                // Add new in-app product details if they're not already present
                inAppDetails.forEach { detail ->
                    if (!productDetails.map { it.productId }.contains(detail.productId)) {
                        productDetails.add(detail)
                    }
                }
            }
        } ?: run {
            ApphudLog.logE("Unable to load INAPP details", false)
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = inAppResult.second
            }
        }
    }

    return responseCode
}
