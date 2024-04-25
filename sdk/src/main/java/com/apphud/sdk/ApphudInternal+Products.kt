package com.apphud.sdk

import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.managers.RequestManager
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.SkuDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal var productsStatus = ApphudProductsStatus.none
internal var respondedWithProducts = false
private  var loadingStoreProducts = false
internal var productsResponseCode = BillingClient.BillingResponseCode.OK
private val mutexProducts = Mutex()

// to avoid Google servers spamming if there is no productDetails added at all
internal var productsLoadingCounts: Int = 0
const val MAX_TOTAL_PRODUCTS_RETRIES: Int = 100

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
            skuDetails.isEmpty() && productsLoadingCounts < MAX_TOTAL_PRODUCTS_RETRIES
        }
    }
}

internal fun ApphudInternal.loadProducts() {
    if (!shouldLoadProducts()) { return }

    productsStatus = ApphudProductsStatus.loading
    ApphudLog.logI("Loading ProductDetails from the Store")

    coroutineScope.launch {
        delay(APPHUD_DEFAULT_MAX_TIMEOUT*1000)
        if (loadingStoreProducts) {
            delay(5000)
        }
        if (!respondedWithProducts) {
            ApphudLog.logI("Force respondWithProducts")
            respondWithProducts()
        }
    }

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

                if (isRetriableProductsRequest() && productsLoadingCounts < maxProductRetriesCount) {
                    retryProductsLoad()
                } else {
                    ApphudLog.log("Finished Loading Product Details")
                    respondWithProducts()
                }
            }
        }
    }
}

private fun respondWithProducts() {
    respondedWithProducts = true
    ApphudInternal.mainScope.launch {
        ApphudInternal.notifyLoadingCompleted(null, ApphudInternal.skuDetails, false, false)
    }
}

internal fun isRetriableProductsRequest(): Boolean {
    return ApphudInternal.skuDetails.isEmpty() && productsStatus == ApphudProductsStatus.failed && isRetriableErrorCode(
        productsResponseCode) && ApphudInternal.isActive && !ApphudUtils.isEmulator()
}

internal fun ApphudInternal.retryProductsLoad() {
    val delay: Long = 500 * productsLoadingCounts.toLong()
    ApphudLog.logE("Failed to load products from store (${ApphudBillingResponseCodes.getName(
        productsResponseCode)}), will retry in ${delay} ms")
    Thread.sleep(delay)
    ApphudInternal.loadProducts()
}

private fun isRetriableErrorCode(code: Int): Boolean {
    return listOf(
        BillingClient.BillingResponseCode.NET_NOT_WORK,
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.ERROR
    ).contains(code)
}

private suspend fun ApphudInternal.fetchProducts(): Int {
    var permissionGroupsCopy = getPermissionGroups()
    if (permissionGroupsCopy.isEmpty() || storage.needUpdateProductGroups()) {
        RequestManager.allProducts()?.let { groups ->
            cacheGroups(groups)
            permissionGroupsCopy = groups
        }
    }
    val ids = allAvailableProductIds(permissionGroupsCopy, getPaywalls())
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
    return ids.toSet().toList()
}

internal const val PRODUCTS_DEFAULT_ERROR = -100
internal suspend fun ApphudInternal.fetchDetails(ids: List<String>): Int {
    // Assuming ProductDetails has a property 'id' that corresponds to the product ID
    val existingIds = synchronized(skuDetails) { skuDetails.map { it.sku } }

    val idsToFetch = ids.filterNot { existingIds.contains(it) }

    // If none ids to load, return immediately
    if (idsToFetch.isEmpty()) {
        ApphudLog.log("NO REQUEST TO FETCH PRODUCT DETAILS")
        return APPHUD_NO_REQUEST
    }

    ApphudLog.log("Fetching Product Details: ${idsToFetch.toString()}")
    loadingStoreProducts = true
    if (productsStatus != ApphudProductsStatus.loading) {
        productsStatus = ApphudProductsStatus.loading
    }

    val startTime = System.currentTimeMillis()

    var responseCode = BillingClient.BillingResponseCode.OK

    coroutineScope {
        var subsResult :Pair<List<SkuDetails>?, Int> = Pair(listOf(), PRODUCTS_DEFAULT_ERROR)
        var inAppResult :Pair<List<SkuDetails>?, Int> = Pair(listOf(), PRODUCTS_DEFAULT_ERROR)

        for (i in 0..10) {
            ApphudLog.log("=====> Attempt: ${i}")
            var exit = true

            if(subsResult.second == PRODUCTS_DEFAULT_ERROR){
                subsResult = async { billing.detailsEx(BillingClient.SkuType.SUBS, idsToFetch) }.await()
            }
            if(inAppResult.second == PRODUCTS_DEFAULT_ERROR){
                inAppResult = async { billing.detailsEx(BillingClient.SkuType.INAPP, idsToFetch) }.await()
            }

            if(subsResult.second == PRODUCTS_DEFAULT_ERROR || inAppResult.second == PRODUCTS_DEFAULT_ERROR){
                exit = false
            }

            if(exit) {
                break
            }
        }

        subsResult.first?.let { subsDetails ->
            ApphudLog.log("=====> SUBS: ${subsDetails.map { it.sku }.joinToString()}")
            synchronized(skuDetails) {
                // Add new subscription details if they're not already present
                subsDetails.forEach { detail ->
                    if (!skuDetails.map { it.sku }.contains(detail.sku)) {
                        skuDetails.add(detail)
                    }
                }
            }
        } ?: run {
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = subsResult.second
            }
        }

        inAppResult.first?.let { inAppDetails ->
            synchronized(skuDetails) {
                ApphudLog.log("=====> INAPPS: ${inAppDetails.map { it.sku }.joinToString()}")
                // Add new in-app product details if they're not already present
                inAppDetails.forEach { detail ->
                    if (!skuDetails.map { it.sku }.contains(detail.sku)) {
                        skuDetails.add(detail)
                    }
                }
            }
        } ?: run {
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = inAppResult.second
            }
        }
    }

    val benchmark = System.currentTimeMillis() - startTime
    loadingStoreProducts = false
    ApphudInternal.productsLoadedTime = benchmark

    return responseCode
}
