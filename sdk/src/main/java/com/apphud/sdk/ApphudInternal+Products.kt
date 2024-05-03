package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

internal var productsStatus = ApphudProductsStatus.none
internal var respondedWithProducts = false
private  var loadingStoreProducts = false
internal var productsResponseCode = BillingClient.BillingResponseCode.OK
private val mutexProducts = Mutex()

// to avoid Google servers spamming if there is no productDetails added at all
internal var currentPoductsLoadingCounts: Int = 0
internal var totalPoductsLoadingCounts: Int = 0
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
            productDetails.isEmpty() && totalPoductsLoadingCounts < MAX_TOTAL_PRODUCTS_RETRIES
        }
    }
}

internal fun ApphudInternal.loadProducts() {
    if (!shouldLoadProducts()) {
        if (totalPoductsLoadingCounts >= MAX_TOTAL_PRODUCTS_RETRIES) {
            respondWithProducts()
        }
        return
    }

    productsStatus = ApphudProductsStatus.loading
    ApphudLog.logI("Loading ProductDetails from the Store")

    coroutineScope.launch(errorHandler) {
        mutexProducts.withLock {
            val result = fetchProducts()
            productsResponseCode = result
            productsStatus = if (result == BillingClient.BillingResponseCode.OK) ApphudProductsStatus.loaded else
                ApphudProductsStatus.failed

            if (productsResponseCode != APPHUD_NO_REQUEST) {
                totalPoductsLoadingCounts += 1
                currentPoductsLoadingCounts += 1
            }

            if (isRetriableProductsRequest() && shouldRetryRequest("billing") && currentPoductsLoadingCounts < APPHUD_DEFAULT_RETRIES) {
                retryProductsLoad()
            } else {
                ApphudLog.log("Finished Loading Product Details")
                respondWithProducts()
            }
        }
    }
}

private fun respondWithProducts() {
    respondedWithProducts = true
    ApphudInternal.mainScope.launch {
        ApphudInternal.notifyLoadingCompleted(null, ApphudInternal.productDetails, false, false)
    }
}

internal fun isRetriableProductsRequest(): Boolean {
    return ApphudInternal.productDetails.isEmpty() && productsStatus == ApphudProductsStatus.failed && isRetriableErrorCode(
        productsResponseCode) && ApphudInternal.isActive && !ApphudUtils.isEmulator()
}

internal fun retryProductsLoad() {
    val delay: Long = 300
    ApphudLog.logE("Failed to load products from store (${ApphudBillingResponseCodes.getName(
        productsResponseCode)}), will retry in $delay ms")
    Thread.sleep(delay)
    ApphudInternal.loadProducts()
}

private fun isRetriableErrorCode(code: Int): Boolean {
    return listOf(
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.ERROR,
        APPHUD_NO_REQUEST
    ).contains(code)
}

private suspend fun awaitUserRegistered(): ApphudUser? =
    suspendCancellableCoroutine { continuation ->
        ApphudInternal.performWhenUserRegistered {
            continuation.resume(ApphudInternal.currentUser)
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

    if (permissionGroupsCopy.isEmpty() && getPaywalls().isEmpty()) {
        ApphudLog.log("Awaiting for user registration before proceeding to products load")
        awaitUserRegistered()
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

internal suspend fun ApphudInternal.fetchDetails(ids: List<String>): Int {
    // Assuming ProductDetails has a property 'id' that corresponds to the product ID
    val existingIds = synchronized(productDetails) { productDetails.map { it.productId } }

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
