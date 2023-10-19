package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

internal var productsLoaded = AtomicInteger(0) //to know that products already loaded by another thread
private val mutexProducts = Mutex()
internal fun ApphudInternal.loadProducts(){
    coroutineScope.launch(errorHandler) {
        mutexProducts.withLock {
            async {
                if (productsLoaded.get() == 0) {
                    if (fetchProducts()) {
                        //Let to know to another threads that details are loaded successfully
                        productsLoaded.incrementAndGet()

                        mainScope.launch {
                            notifyLoadingCompleted(
                                null,
                                productDetails
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun ApphudInternal.fetchProducts(): Boolean {
    val cachedGroups = storage.productGroups
    if(cachedGroups == null || storage.needUpdateProductGroups()){
        val groupsList = RequestManager.allProducts()
        groupsList?.let { groups ->
            cacheGroups(groups)
            val ids = groups.map { it -> it.products?.map { it.product_id }!! }.flatten()
            return fetchDetails(ids)
        }
    }else{
        val ids = cachedGroups.map { it -> it.products?.map { it.product_id }!! }.flatten()
        return fetchDetails(ids)
    }
    return false
}

internal suspend fun ApphudInternal.fetchDetails(ids :List<String>): Boolean {
    var isInapLoaded = false
    var isSubsLoaded = false
    synchronized(productDetails) {
        productDetails.clear()
    }

    coroutineScope {
        val subs = async{ billing.detailsEx(BillingClient.ProductType.SUBS, ids)}
        val inap =  async{ billing.detailsEx(BillingClient.ProductType.INAPP, ids)}

        subs.await()?.let {
            synchronized(productDetails) {
                productDetails.addAll(it)

                for(item in productDetails) {
                    ApphudLog.log(item.zza())
                }
            }
            isSubsLoaded = true
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details", false)
        }

        inap.await()?.let {
            synchronized(productDetails) {
                productDetails.addAll(it)
                for(item in productDetails) {
                    ApphudLog.log(item.name  + ":  " + item.toString())
                }
            }
            isInapLoaded = true
        } ?: run {
            ApphudLog.logE("Unable to load INAP details", false)
        }
    }
    return isSubsLoaded && isInapLoaded
}