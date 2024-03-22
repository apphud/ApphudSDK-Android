package com.apphud.sdk

import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.managers.RequestManager
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.SkuDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

internal var productsStatus = ApphudProductsStatus.none
internal var productsResponseCode = BillingClient.BillingResponseCode.OK
private val mutexProducts = Mutex()

// to avoid Google servers spamming if there is no productDetails added at all
internal var productsLoadingCounts: Int = 0
const val MAX_PRODUCTS_RETRIES: Int = 100

internal enum class ApphudProductsStatus {
    none,
    loading,
    loaded,
    failed
}

internal fun ApphudInternal.finishedLoadingProducts(): Boolean {
    return productsStatus == ApphudProductsStatus.loaded || productsStatus == ApphudProductsStatus.failed
}

internal fun ApphudInternal.shouldLoadProducts(): Boolean {
    return when (productsStatus) {
        ApphudProductsStatus.none -> true
        ApphudProductsStatus.loading -> false
        else -> {
            skuDetails.isEmpty() && productsLoadingCounts < MAX_PRODUCTS_RETRIES
        }
    }
}

internal fun ApphudInternal.loadProducts() {
    if (!shouldLoadProducts()) { return }

    productsStatus = ApphudProductsStatus.loading
    ApphudLog.logI("Loading ProductDetails from the Store")

    coroutineScope.launch(errorHandler) {
        mutexProducts.withLock {
            async {
                val result = fetchProducts()
                productsResponseCode = result
                productsStatus = if (result == BillingClient.BillingResponseCode.OK) ApphudProductsStatus.loaded else
                    ApphudProductsStatus.failed

                if (productsResponseCode != APPHUD_NO_REQUEST) {
                    productsLoadingCounts += 1
                }

                mainScope.launch {
                    notifyLoadingCompleted(null, skuDetails, false, false)
                }
            }
        }
    }
}

private suspend fun ApphudInternal.fetchProducts(): Int {
    var permissionGroupsCopy = getPermissionGroups()
    if (permissionGroupsCopy.isEmpty() || storage.needUpdateProductGroups()) {
        RequestManager.allProducts()?.let { groups ->
            cacheGroups(groups)
            permissionGroupsCopy = groups
        }
    }
    val ids = allAvailableProductIds(permissionGroupsCopy, paywalls)
    return fetchDetails(ids)
}

private fun allAvailableProductIds(groups: List<ApphudGroup>, paywalls: List<ApphudPaywall>): List<String> {
    val ids = paywalls.map { p -> p.products?.map { it.productId } ?: listOf() }.flatten().toMutableList()
    val idsPaywall = groups.map { it -> it.products?.map { it.productId } ?: listOf() }.flatten()
    idsPaywall.forEach {
        if (!ids.contains(it) && it != null) {
            ids.add(it)
        }
    }
    return ids
}

internal suspend fun ApphudInternal.fetchDetails(ids: List<String>): Int {
    // Assuming ProductDetails has a property 'id' that corresponds to the product ID
    val existingIds = synchronized(skuDetails) { skuDetails.map { it.sku } }

    val idsToFetch = ids.filterNot { existingIds.contains(it) }

    // If none ids to load, return immediately
    if (idsToFetch.isEmpty()) {
        return APPHUD_NO_REQUEST
    }

    ApphudLog.log("Fetching Product Details: ${idsToFetch.toString()}")

    if (productsStatus != ApphudProductsStatus.loading) {
        productsStatus = ApphudProductsStatus.loading
    }

    var responseCode = BillingClient.BillingResponseCode.OK

    coroutineScope {
        val subsResult = async { billing.detailsEx(BillingClient.SkuType.SUBS, idsToFetch) }.await()
        val inAppResult = async { billing.detailsEx(BillingClient.SkuType.INAPP, idsToFetch) }.await()

        subsResult.first?.let { subsDetails ->
            synchronized(skuDetails) {
                // Add new subscription details if they're not already present
                subsDetails.forEach { detail ->
                    if (!skuDetails.map { it.sku }.contains(detail.sku)) {
                        skuDetails.add(detail)
                    }
                }
            }
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details: ${subsResult.second}", false)
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = subsResult.second
            }
        }

        inAppResult.first?.let { inAppDetails ->
            synchronized(skuDetails) {
                // Add new in-app product details if they're not already present
                inAppDetails.forEach { detail ->
                    if (!skuDetails.map { it.sku }.contains(detail.sku)) {
                        skuDetails.add(detail)
                    }
                }
            }
        } ?: run {
            ApphudLog.logE("Unable to load INAPP details: ${inAppResult.second}", false)
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = inAppResult.second
            }
        }
    }

    return responseCode
}
