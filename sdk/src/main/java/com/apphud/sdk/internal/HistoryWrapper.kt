package com.apphud.sdk.internal

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.Closeable

internal class HistoryWrapper(
    private val billing: BillingClient,
) : Closeable {

    suspend fun queryPurchasesSync(): Pair<List<Purchase>, Int> = coroutineScope {
        val paramsSubs = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val subsDeferred = CompletableDeferred<List<Purchase>>()

        var responseResult = BillingClient.BillingResponseCode.OK

        billing.queryPurchasesAsync(paramsSubs) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                subsDeferred.complete(purchases)
            } else {
                if (responseResult == BillingClient.BillingResponseCode.OK) {
                    responseResult = result.responseCode
                }
                subsDeferred.complete(emptyList())
            }
        }

        val paramsInApps = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val inAppsDeferred = CompletableDeferred<List<Purchase>>()

        billing.queryPurchasesAsync(paramsInApps) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                inAppsDeferred.complete(purchases)
            } else {
                if (responseResult == BillingClient.BillingResponseCode.OK) {
                    responseResult = result.responseCode
                }
                inAppsDeferred.complete(emptyList())
            }
        }

        val subsPurchases = async { subsDeferred.await() }
        val inAppsPurchases = async { inAppsDeferred.await() }

        val finalPurchases = subsPurchases.await() + inAppsPurchases.await()

        return@coroutineScope Pair(finalPurchases, responseResult)
    }

    override fun close() {
    }
}
