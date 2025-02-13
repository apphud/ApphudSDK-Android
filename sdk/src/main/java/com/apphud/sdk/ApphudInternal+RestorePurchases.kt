package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchaseHistoryRecord
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.callback_status.PurchaseHistoryCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun ApphudInternal.restorePurchases(callback: ApphudPurchasesRestoreCallback) {
    ApphudLog.log("User called: SyncPurchases()")
    syncPurchases(observerMode = false, callback = callback)
}

private val mutexSync = Mutex()

private var unvalidatedPurchases = listOf<Purchase>()

internal suspend fun ApphudInternal.fetchNativePurchases(
    forceRefresh: Boolean = false,
    needSync: Boolean = true,
): Pair<List<Purchase>, Int> {
    var responseCode = BillingClient.BillingResponseCode.OK
    if (unvalidatedPurchases.isEmpty() || forceRefresh) {
        val result = billing.queryPurchasesSync()
        val purchases = result.first
        responseCode = result.second
        if (!purchases.isNullOrEmpty()) {
            unvalidatedPurchases = purchases
            val notSyncedPurchases = filterNotSynced(unvalidatedPurchases)
            if (needSync) {
                if (notSyncedPurchases.isNotEmpty()) {
                    syncPurchases(unvalidatedPurchs = notSyncedPurchases)
                } else {
                    ApphudLog.logI("Google Billing: All Tracked (${unvalidatedPurchases.count()})")
                }
            }
        } else {
            ApphudLog.logI("Google Billing: No Active Purchases")
        }
    }
    return Pair(unvalidatedPurchases, responseCode)
}

private fun ApphudInternal.filterNotSynced(allPurchases: List<Purchase>): List<Purchase> {
    val allKnownTokens = (currentUser?.subscriptions?.map { it.purchaseToken }
        ?: listOf<String>()) + (currentUser?.purchases?.map { it.purchaseToken } ?: listOf<String>())
    return allPurchases.filter { !allKnownTokens.contains(it.purchaseToken) }
}

private suspend fun queryPurchases(): List<Purchase> {
    val result = ApphudInternal.billing.queryPurchasesSync()
    return result.first ?: listOf()
}

internal fun ApphudInternal.syncPurchases(
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    observerMode: Boolean = true,
    unvalidatedPurchs: List<Purchase>? = null,
    callback: ApphudPurchasesRestoreCallback? = null,
) {
    performWhenUserRegistered { error ->
        error?.let {
            ApphudLog.log("SyncPurchases: performWhenUserRegistered fail")
            callback?.invoke(null, null, error)
        } ?: run {
            coroutineScope.launch(errorHandler) {
                mutexSync.withLock {

                    var queriedPurchases = unvalidatedPurchs

                    ApphudLog.log("SyncPurchases: start")
                    val subsResult = billing.queryPurchaseHistorySync(BillingClient.ProductType.SUBS)
                    val inapsResult = billing.queryPurchaseHistorySync(BillingClient.ProductType.INAPP)
                    var purchases = mutableListOf<PurchaseHistoryRecord>()
                    purchases.addAll(processHistoryCallbackStatus(subsResult))

                    if (purchases.count() > 10 && queriedPurchases.isNullOrEmpty()) {
                        ApphudLog.log("Found ${purchases.count()} subscriptions during restorePurchases call. Will send only active of them.")
                        // more than 10 subscriptions in history, assuming this is sandbox account
                        queriedPurchases = queryPurchases()
                    }

                    purchases.addAll(processHistoryCallbackStatus(inapsResult))

                    if (!queriedPurchases.isNullOrEmpty()) {
                        val tokens = queriedPurchases.map { it.purchaseToken }
                        val filtered = purchases.filter { tokens.contains(it.purchaseToken) }
                        if (filtered.isNotEmpty()) {
                            purchases = filtered.toMutableList()
                        }
                    }

                    if (purchases.isEmpty()) {
                        ApphudLog.log(message = "SyncPurchases: Nothing to restore", sendLogToServer = false)
                        storage.isNeedSync = false
                        mainScope.launch {
                            refreshEntitlements(true)
                            currentUser?.let {
                                callback?.invoke(it.subscriptions, it.purchases, null)
                            }
                        }
                    } else {
                        ApphudLog.log("SyncPurchases: Products to restore: ${purchases.map { it.products.firstOrNull() ?: "" }} ")

                        val restoredPurchases = mutableListOf<PurchaseRecordDetails>()
                        val purchasesToLoadDetails = mutableListOf<PurchaseHistoryRecord>()

                        val loadedProductIds = productDetails.map { it.productId }
                        for (purchase in purchases) {
                            if (loadedProductIds.containsAll(purchase.products)) {
                                val details = productDetails.find { it.productId == purchase.products[0] }
                                details?.let {
                                    restoredPurchases.add(PurchaseRecordDetails(purchase, it))
                                }
                            } else {
                                purchasesToLoadDetails.add(purchase)
                            }
                        }

                        if (purchasesToLoadDetails.isNotEmpty()) {
                            ApphudLog.log("SyncPurchases: Load product details for: ${purchasesToLoadDetails.map { it.products.firstOrNull() ?: "" }}")
                            val subsRestored =
                                billing.restoreSync(BillingClient.ProductType.SUBS, purchasesToLoadDetails)
                            val inapsRestored =
                                billing.restoreSync(BillingClient.ProductType.INAPP, purchasesToLoadDetails)

                            restoredPurchases.addAll(processRestoreCallbackStatus(subsRestored))
                            restoredPurchases.addAll(processRestoreCallbackStatus(inapsRestored))
                        } else {
                            ApphudLog.log("SyncPurchases: All products details already loaded.")
                        }

                        ApphudLog.log("Start syncing ${restoredPurchases.count()} in-app purchases: ${restoredPurchases.map { it.details.productId }}")

                        if (prevPurchases.containsAll(restoredPurchases)) {
                            ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                            storage.isNeedSync = false
                            mainScope.launch {
                                refreshEntitlements(true)
                            }
                        } else {
                            sendPurchasesToApphud(
                                paywallIdentifier = paywallIdentifier,
                                placementIdentifier = placementIdentifier,
                                tempPurchaseRecordDetails = restoredPurchases,
                                callback = callback,
                                observerMode = observerMode,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal suspend fun ApphudInternal.sendPurchasesToApphud(
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    tempPurchaseRecordDetails: List<PurchaseRecordDetails>?,
    callback: ApphudPurchasesRestoreCallback? = null,
    observerMode: Boolean,
) {
    val apphudProduct: ApphudProduct? =
        findJustPurchasedProduct(paywallIdentifier, placementIdentifier, null, tempPurchaseRecordDetails)

    fun notifyError() {
        val message = "Failed to restore purchases"
        ApphudLog.logE(message = message)
        mainScope.launch {
            callback?.invoke(null, null, ApphudError(message))
        }
    }

    tempPurchaseRecordDetails ?: run {
        notifyError()
        return
    }

    runCatchingCancellable {
        RequestManager.restorePurchases(
            apphudProduct,
            tempPurchaseRecordDetails,
            observerMode,
        )
    }
        .onSuccess { customer ->
            if (tempPurchaseRecordDetails.isNotEmpty() &&
                (customer.subscriptions.size + customer.purchases.size) == 0
            ) {
                val message =
                    """
                    Unable to completely validate all purchases.
                    Ensure Google Service Credentials are correct and have necessary permissions.
                    Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support.
                    """.trimIndent()

                ApphudLog.logE(message = message)
            } else {
                ApphudLog.log("SyncPurchases: customer was successfully updated $customer")
            }

            storage.isNeedSync = false
            prevPurchases.addAll(tempPurchaseRecordDetails)

            userId = customer.userId

            withContext(Dispatchers.Main) {
                ApphudLog.log("SyncPurchases: success $customer")
                notifyLoadingCompleted(customer)
                callback?.invoke(customer.subscriptions, customer.purchases, null)
            }
        }
        .onFailure { notifyError() }
}

private fun processHistoryCallbackStatus(result: PurchaseHistoryCallbackStatus): List<PurchaseHistoryRecord> {
    when (result) {
        is PurchaseHistoryCallbackStatus.Error -> {
            val type = if (result.type() == BillingClient.ProductType.SUBS) "subscriptions" else "in-app products"
            ApphudLog.log(
                "Failed to load history for $type with error: (" +
                        "${result.result?.responseCode})" +
                        "${result.result?.debugMessage})",
            )
        }
        is PurchaseHistoryCallbackStatus.Success -> {
            return result.purchases
        }
    }
    return emptyList()
}

private fun processRestoreCallbackStatus(result: PurchaseRestoredCallbackStatus): List<PurchaseRecordDetails> {
    when (result) {
        is PurchaseRestoredCallbackStatus.Error -> {
            val type = if (result.type() == BillingClient.ProductType.SUBS) "subscriptions" else "in-app products"
            val error =
                ApphudError(
                    message = "Restore Purchases is failed for $type",
                    secondErrorMessage = result.message,
                    errorCode = result.result?.responseCode,
                )
            ApphudLog.log(message = error.toString(), sendLogToServer = true)
        }
        is PurchaseRestoredCallbackStatus.Success -> {
            return result.purchases
        }
    }
    return emptyList()
}

private fun ApphudInternal.findJustPurchasedProduct(
    paywallIdentifier: String?,
    placementIdentifier: String?,
    productDetails: ProductDetails?,
    tempPurchaseRecordDetails: List<PurchaseRecordDetails>?,
): ApphudProduct? {
    try {
        val targetPaywall =
            if (placementIdentifier != null) {
                placements.firstOrNull { it.identifier == placementIdentifier }?.paywall
            } else {
                paywalls.firstOrNull { it.identifier == paywallIdentifier }
            }

        productDetails?.let { details ->
            return targetPaywall?.products?.find { it.productDetails?.productId == details.productId }
        }

        val record = tempPurchaseRecordDetails?.maxByOrNull { it.record.purchaseTime }
        record?.let { rec ->
            val offset = System.currentTimeMillis() - rec.record.purchaseTime
            if (offset < 300000L) { // 5 min
                return targetPaywall?.products?.find { it.productId == rec.details.productId }
            }
        }
    } catch (ex: Exception) {
        ex.message?.let {
            ApphudLog.logE(message = it)
        }
    }
    return null
}
