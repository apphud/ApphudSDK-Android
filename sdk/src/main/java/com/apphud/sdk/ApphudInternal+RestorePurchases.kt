package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.callback_status.PurchaseRestoredCallbackStatus
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal suspend fun ApphudInternal.restorePurchases(): ApphudPurchasesRestoreResult {
    ApphudLog.log("User called: SyncPurchases()")
    return syncPurchases(observerMode = false)
}

private val mutexSync = Mutex()

internal suspend fun ApphudInternal.syncPurchases(
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    observerMode: Boolean = true,
    unvalidatedPurchs: List<Purchase>? = null,
): ApphudPurchasesRestoreResult {
    runCatchingCancellable { awaitUserRegistration() }
        .onFailure { error ->
            ApphudLog.log("SyncPurchases: awaitUserRegistration fail")
            return ApphudPurchasesRestoreResult.Error(error.toApphudError())
        }

    return mutexSync.withLock {
        ApphudLog.log("SyncPurchases: start")

        val purchases: List<Purchase> = if (!unvalidatedPurchs.isNullOrEmpty()) {
            unvalidatedPurchs
        } else {
            val result = billing.queryPurchasesSync()
            result.first ?: emptyList()
        }

        if (purchases.isEmpty()) {
            ApphudLog.log(message = "SyncPurchases: Nothing to restore")
            storage.isNeedSync = false
            refreshEntitlements()
            val user = userRepository.getCurrentUser()
            return if (user != null) {
                ApphudPurchasesRestoreResult.Success(user.subscriptions.toList(), user.purchases.toList())
            } else {
                ApphudPurchasesRestoreResult.Error(ApphudError("User not found."))
            }
        } else {
            ApphudLog.log("SyncPurchases: Products to restore: ${purchases.map { it.products.firstOrNull() ?: "" }} ")

            val restoredPurchases = mutableListOf<PurchaseRecordDetails>()
            val purchasesToLoadDetails = mutableListOf<Purchase>()

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
                val subsRestored = billing.restoreSync(
                    BillingClient.ProductType.SUBS, purchasesToLoadDetails
                )
                val inapsRestored = billing.restoreSync(
                    BillingClient.ProductType.INAPP, purchasesToLoadDetails
                )

                restoredPurchases.addAll(processRestoreCallbackStatus(subsRestored))
                restoredPurchases.addAll(processRestoreCallbackStatus(inapsRestored))
            } else {
                ApphudLog.log("SyncPurchases: All products details already loaded.")
            }

            ApphudLog.log("Start syncing ${restoredPurchases.count()} in-app purchases: ${restoredPurchases.map { it.details.productId }}")

            if (prevPurchases.containsAll(restoredPurchases)) {
                ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                storage.isNeedSync = false
                refreshEntitlements()
                val user = userRepository.getCurrentUser()
                return if (user != null) {
                    ApphudPurchasesRestoreResult.Success(user.subscriptions.toList(), user.purchases.toList())
                } else {
                    ApphudPurchasesRestoreResult.Error(ApphudError("User not found."))
                }
            } else {
                sendPurchasesToApphud(
                    paywallIdentifier = paywallIdentifier,
                    placementIdentifier = placementIdentifier,
                    tempPurchaseRecordDetails = restoredPurchases,
                    observerMode = observerMode,
                )
            }
        }
    }
}

internal suspend fun ApphudInternal.sendPurchasesToApphud(
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    tempPurchaseRecordDetails: List<PurchaseRecordDetails>?,
    observerMode: Boolean,
): ApphudPurchasesRestoreResult {
    val apphudProduct: ApphudProduct? = findJustPurchasedProduct(
        paywallIdentifier,
        placementIdentifier,
        null,
        tempPurchaseRecordDetails
    )

    if (tempPurchaseRecordDetails == null) {
        val message = "Failed to restore purchases"
        ApphudLog.logE(message = message)
        return ApphudPurchasesRestoreResult.Error(ApphudError(message))
    }

    return runCatchingCancellable {
        RequestManager.restorePurchases(
            apphudProduct,
            tempPurchaseRecordDetails,
            observerMode,
        )
    }.fold(
        onSuccess = { customer ->
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

            withContext(dispatchers.main) {
                ApphudLog.log("SyncPurchases: success $customer")
                notifyLoadingCompleted(customerLoaded = customer)
            }

            ApphudPurchasesRestoreResult.Success(customer.subscriptions.toList(), customer.purchases.toList())
        },
        onFailure = { error ->
            ApphudPurchasesRestoreResult.Error(error.toApphudError())
        }
    )
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
            ApphudLog.log(message = error.toString())
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
        val user = userRepository.getCurrentUser()
        val userPlacements = user?.placements.orEmpty()

        val targetPaywall = when {
            placementIdentifier != null ->
                userPlacements.firstOrNull { placement -> placement.identifier == placementIdentifier }?.paywall
            paywallIdentifier != null ->
                userPlacements.firstNotNullOfOrNull { placement -> placement.paywall?.takeIf { paywall -> paywall.identifier == paywallIdentifier } }
            else -> null
        }

        productDetails?.let { details ->
            return targetPaywall?.products?.find { it.productDetails?.productId == details.productId }
        }

        val record = tempPurchaseRecordDetails?.maxByOrNull { it.purchase.purchaseTime }
        record?.let { rec ->
            val offset = System.currentTimeMillis() - rec.purchase.purchaseTime
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
