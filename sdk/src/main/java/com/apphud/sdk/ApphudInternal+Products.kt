package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

internal var productsStatus = ApphudProductsStatus.none
internal var respondedWithProducts = false
private var loadingStoreProducts = false
internal var productsResponseCode = BillingClient.BillingResponseCode.OK
private var loadedDetails = CopyOnWriteArrayList<ProductDetails>()

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

    if (!hasRespondedToPaywallsRequest || deferPlacements) {
        return false
    }

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
        fetchProducts()

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

internal fun respondWithProducts() {
    respondedWithProducts = true
    ApphudInternal.mainScope.launch {
        ApphudInternal.notifyLoadingCompleted(null, loadedDetails.toList(), false, false)
    }
}

internal fun isRetriableProductsRequest(): Boolean {
    return ApphudInternal.productDetails.isEmpty() && productsStatus == ApphudProductsStatus.failed && isRetriableErrorCode(
        productsResponseCode
    ) && ApphudInternal.isActive && !ApphudUtils.isEmulator()
}

internal fun retryProductsLoad() {
    val delay: Long = 300
    ApphudLog.logI(
        "Load products from store status code: (${
            ApphudBillingResponseCodes.getName(
                productsResponseCode
            )
        }), will retry in $delay ms"
    )
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
        BillingClient.BillingResponseCode.ERROR
    ).contains(code)
}

internal suspend fun ApphudInternal.fetchProducts(): Int {
    val user = userRepository.getCurrentUser()
    val userPaywalls = user?.paywalls.orEmpty()
    val userPlacements = user?.placements.orEmpty()
    if (userPlacements.isEmpty() && userPaywalls.isEmpty()) {
        if (user == null) {
            ApphudLog.log("Awaiting for user registration before proceeding to products load")
            awaitUserRegistration()
            ApphudLog.log("User registered, continue to fetch ProductDetails")
        }
    }

    val refreshedUser = userRepository.getCurrentUser()
    val ids = allAvailableProductIds(
        getPermissionGroups(),
        refreshedUser?.paywalls.orEmpty(),
        refreshedUser?.placements.orEmpty()
    )

    return fetchDetails(ids, loadingAll = true).first
}

private fun allAvailableProductIds(
    groups: List<ApphudGroup>,
    paywalls: List<ApphudPaywall>,
    placements: List<ApphudPlacement>,
): List<String> {
    val ids = paywalls.map { p -> p.products?.map { it.productId } ?: listOf() }.flatten().toMutableList()
    val idsGroups = groups.map { it -> it.products?.map { it.productId } ?: listOf() }.flatten()
    val idsFromPlacements =
        placements.map { pl -> pl.paywall?.products?.map { it.productId } ?: listOf() }.flatten().toMutableList()

    idsGroups.forEach {
        if (!ids.contains(it) && it != null) {
            ids.add(it)
        }
    }
    idsFromPlacements.forEach {
        if (!ids.contains(it) && it != null) {
            ids.add(it)
        }
    }

    return ids.toSet().toList()
}

internal suspend fun ApphudInternal.fetchDetails(
    ids: List<String>,
    loadingAll: Boolean = false,
): Pair<Int, List<ProductDetails>?> {
    if (loadingAll) {
        loadedDetails.clear()
    }
    // Assuming ProductDetails has a property 'id' that corresponds to the product ID
    val existingIds = productDetails.map { it.productId }

    val idsToFetch = ids.filterNot { existingIds.contains(it) }

    if (existingIds.isNotEmpty() && idsToFetch.isEmpty()) {
        // All Ids already loaded, return OK
        if (loadingAll) {
            productsStatus = ApphudProductsStatus.loaded
        }
        return Pair(BillingResponseCode.OK, null)
    } else if (idsToFetch.isEmpty()) {
        // If none ids to load, return immediately
        ApphudLog.log("NO REQUEST TO FETCH PRODUCT DETAILS")
        if (loadingAll) {
            productsStatus = ApphudProductsStatus.loaded
        }
        return Pair(APPHUD_NO_REQUEST, null)
    }

    ApphudLog.log("Fetching Product Details: ${idsToFetch.toString()}")
    loadingStoreProducts = true
    if (productsStatus != ApphudProductsStatus.loading && loadingAll) {
        productsStatus = ApphudProductsStatus.loading
    }

    val startTime = System.currentTimeMillis()

    var responseCode = BillingClient.BillingResponseCode.OK

    coroutineScope {
        val subsResult = async { billing.detailsEx(BillingClient.ProductType.SUBS, idsToFetch) }.await()
        val inAppResult = async { billing.detailsEx(BillingClient.ProductType.INAPP, idsToFetch) }.await()

        subsResult.first?.let { subsDetails ->
            // Add new subscription details if they're not already present
            // CopyOnWriteArrayList is thread-safe, no synchronization needed
            subsDetails.forEach { detail ->
                if (!loadedDetails.any { it.productId == detail.productId }) {
                    loadedDetails.add(detail)
                }
            }
        } ?: run {
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = subsResult.second
            }
        }

        inAppResult.first?.let { inAppDetails ->
            // Add new in-app product details if they're not already present
            // CopyOnWriteArrayList is thread-safe, no synchronization needed
            inAppDetails.forEach { detail ->
                if (!loadedDetails.any { it.productId == detail.productId }) {
                    loadedDetails.add(detail)
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

    if (loadingAll) {
        productsResponseCode = responseCode
        productsStatus = if (responseCode == BillingClient.BillingResponseCode.OK) ApphudProductsStatus.loaded else
            ApphudProductsStatus.failed
    }

    return Pair(responseCode, loadedDetails)
}
