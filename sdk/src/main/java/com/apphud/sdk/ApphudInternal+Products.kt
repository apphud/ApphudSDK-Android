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

internal var productsLoaded = AtomicBoolean(false)
private val mutexProducts = Mutex()

internal fun ApphudInternal.loadProducts() {
    if (productsLoaded.get()) { return }

    coroutineScope.launch(errorHandler) {
        mutexProducts.withLock {
            async {
                if (fetchProducts()) {
                    // Let to know to another threads that details are loaded successfully
                    productsLoaded.set(true)

                    mainScope.launch {
                        notifyLoadingCompleted(null, productDetails)
                    }
                }
            }
        }
    }
}

private suspend fun ApphudInternal.fetchProducts(): Boolean {
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
    return false
}

internal suspend fun ApphudInternal.fetchDetails(ids: List<String>): Boolean {
    var subsDetails: List<ProductDetails>? = null
    var inAppDetails: List<ProductDetails>? = null

    coroutineScope {
        val subs = async { billing.detailsEx(BillingClient.ProductType.SUBS, ids) }
        val inApp = async { billing.detailsEx(BillingClient.ProductType.INAPP, ids) }

        subs.await()?.let {
            subsDetails = it
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details", false)
        }

         inApp.await()?.let {
            inAppDetails = it
        } ?: run {
            ApphudLog.logE("Unable to load INAP details", false)
        }
    }

    synchronized(productDetails) {
        productDetails.clear()
        subsDetails?.let {
            productDetails.addAll(it)
        }
        inAppDetails?.let {
            productDetails.addAll(it)
        }
    }

    return subsDetails != null && inAppDetails != null
}
