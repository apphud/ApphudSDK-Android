package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.internal.data.ProductLoadingState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MAX_TOTAL_PRODUCTS_RETRIES: Int = 100

internal fun ApphudInternal.finishedLoadingProducts(): Boolean {
    return productRepository.state.value.isFinished
}

internal fun ApphudInternal.shouldLoadProducts(): Boolean {

    if (!hasRespondedToPaywallsRequest || deferPlacements) {
        return false
    }

    return when (val state = productRepository.state.value) {
        is ProductLoadingState.Idle -> true
        is ProductLoadingState.Loading -> false
        is ProductLoadingState.Success -> false
        is ProductLoadingState.Failed -> {
            state.cachedProducts.isEmpty() && state.totalRetryCount < MAX_TOTAL_PRODUCTS_RETRIES
        }
    }
}

internal fun ApphudInternal.loadProducts() {
    if (!shouldLoadProducts()) {
        val state = productRepository.state.value
        if (state is ProductLoadingState.Failed && state.totalRetryCount >= MAX_TOTAL_PRODUCTS_RETRIES) {
            respondWithProducts()
        }
        return
    }

    productRepository.transitionToLoading()
    ApphudLog.logI("Loading ProductDetails from the Store")

    coroutineScope.launch(errorHandler) {
        val responseCode = fetchProducts()

        if (responseCode == APPHUD_NO_REQUEST) {
            productRepository.rollbackRetryCounters()
        }

        if (isRetriableProductsRequest() && shouldRetryRequest("billing")) {
            retryProductsLoad()
        } else {
            ApphudLog.log("Finished Loading Product Details")
            respondWithProducts()
        }
    }
}

internal fun respondWithProducts() {
    ApphudInternal.productRepository.markAsResponded()
    ApphudInternal.mainScope.launch {
        ApphudInternal.notifyLoadingCompleted(null, ApphudInternal.productRepository.state.value.products, false, false)
    }
}

internal fun isRetriableProductsRequest(): Boolean {
    val state = ApphudInternal.productRepository.state.value
    return state is ProductLoadingState.Failed &&
        state.isRetriable &&
        ApphudInternal.isActive &&
        !ApphudUtils.isEmulator()
}

internal suspend fun retryProductsLoad() {
    val delayMs: Long = 300
    val state = ApphudInternal.productRepository.state.value
    val responseCode = if (state is ProductLoadingState.Failed) state.responseCode else BillingResponseCode.OK
    ApphudLog.logI(
        "Load products from store status code: (${
            ApphudBillingResponseCodes.getName(responseCode)
        }), will retry in $delayMs ms"
    )
    delay(delayMs)
    ApphudInternal.loadProducts()
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
        if (!ids.contains(it)) {
            ids.add(it)
        }
    }
    idsFromPlacements.forEach {
        if (!ids.contains(it)) {
            ids.add(it)
        }
    }

    return ids.toSet().toList()
}

internal suspend fun ApphudInternal.fetchDetails(
    ids: List<String>,
    loadingAll: Boolean = false,
): Pair<Int, List<ProductDetails>?> {
    val tempLoadedDetails = mutableListOf<ProductDetails>()

    val existingIds = productRepository.state.value.products.map { it.productId }

    val idsToFetch = ids.filterNot { existingIds.contains(it) }

    if (existingIds.isNotEmpty() && idsToFetch.isEmpty()) {
        // All requested IDs already loaded in state
        // Don't call transitionToSuccess - it would replace all products with just the requested subset!
        return Pair(BillingResponseCode.OK, null)
    } else if (idsToFetch.isEmpty()) {
        // If none ids to load, return immediately
        // This happens when user/paywall has no products configured
        ApphudLog.log("NO REQUEST TO FETCH PRODUCT DETAILS")
        // Don't transition to Success (requires at least one product)
        // Leave state as-is
        return Pair(APPHUD_NO_REQUEST, null)
    }

    ApphudLog.log("Fetching Product Details: ${idsToFetch.toString()}")

    val startTime = System.currentTimeMillis()

    var responseCode = BillingClient.BillingResponseCode.OK

    coroutineScope {
        val subsResult = async { billing.detailsEx(BillingClient.ProductType.SUBS, idsToFetch) }.await()
        val inAppResult = async { billing.detailsEx(BillingClient.ProductType.INAPP, idsToFetch) }.await()

        subsResult.first?.let { subsDetails ->
            subsDetails.forEach { detail ->
                if (!tempLoadedDetails.any { it.productId == detail.productId }) {
                    tempLoadedDetails.add(detail)
                }
            }
        } ?: run {
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = subsResult.second
            }
        }

        inAppResult.first?.let { inAppDetails ->
            inAppDetails.forEach { detail ->
                if (!tempLoadedDetails.any { it.productId == detail.productId }) {
                    tempLoadedDetails.add(detail)
                }
            }
        } ?: run {
            if (responseCode == BillingClient.BillingResponseCode.OK) {
                responseCode = inAppResult.second
            }
        }
    }

    val benchmark = System.currentTimeMillis() - startTime
    ApphudInternal.productsLoadedTime = benchmark

    if (loadingAll) {
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (tempLoadedDetails.isNotEmpty()) {
                productRepository.transitionToSuccess(tempLoadedDetails, loadTimeMs = benchmark)
            } else {
                val currentProducts = productRepository.state.value.products
                if (currentProducts.isEmpty()) {
                    productRepository.transitionToFailed(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
                } else {
                    productRepository.transitionToSuccess(currentProducts, loadTimeMs = benchmark)
                }
            }
        } else {
            productRepository.transitionToFailed(responseCode)
        }
    }

    return Pair(responseCode, tempLoadedDetails)
}
