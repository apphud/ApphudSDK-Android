package com.apphud.sdk

import com.apphud.sdk.managers.RequestManager
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.SkuDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

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
                        notifyLoadingCompleted(null, skuDetails)
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
    var subsDetails: List<SkuDetails>? = null
    var inAppDetails: List<SkuDetails>? = null

    coroutineScope {
        val subs = async { billing.detailsEx(BillingClient.SkuType.SUBS, ids) }
        val inApp = async { billing.detailsEx(BillingClient.SkuType.INAPP, ids) }

        ApphudLog.log("Load details for:  ${ids}", false)

        subs.await()?.let {
            subsDetails = it
            ApphudLog.log("Loaded SUBS details ${it.size}", false)
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details", false)
        }

        inApp.await()?.let {
            inAppDetails = it
            ApphudLog.log("Loaded INAP details ${it.size}", false)
        } ?: run {
            ApphudLog.logE("Unable to load INAP details", false)
        }
    }

    synchronized(skuDetails) {
        skuDetails.clear()
        subsDetails?.let {
            skuDetails.addAll(it)
        }
        inAppDetails?.let {
            skuDetails.addAll(it)
        }
    }

    ApphudLog.log("Loaded skuDetails ${skuDetails.size} result:${subsDetails != null && inAppDetails != null}", false)
    return subsDetails != null && inAppDetails != null
}
