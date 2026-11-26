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
    return syncPurchases(observerMode = false, restore = true)
}

private val mutexSync = Mutex()

/**
 * Nothing to send, or an automatic sync of purchases already sent. A restore is always sent: the
 * purchases may belong to another customer until it moves them. [identityVersion1]: a restore of
 * purchases already sent is skipped too, as SDK 3.6 did.
 */
internal fun skipsSync(
    restore: Boolean,
    prevPurchases: Collection<PurchaseRecordDetails>,
    restoredPurchases: Collection<PurchaseRecordDetails>,
    identityVersion1: Boolean = false,
): Boolean = restoredPurchases.isEmpty() ||
    ((!restore || identityVersion1) && prevPurchases.containsAll(restoredPurchases))

internal suspend fun ApphudInternal.syncPurchases(
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    observerMode: Boolean = true,
    unvalidatedPurchs: List<Purchase>? = null,
    // Only the restore the user asked for; it moves a purchase another customer owns.
    restore: Boolean = false,
): ApphudPurchasesRestoreResult =
    performSync(paywallIdentifier, placementIdentifier, observerMode, unvalidatedPurchs, restore, reportFailures = false)

/**
 * The observer-mode sync with a result that tells whether the backend answered for the purchases:
 * Google Play, network and backend failures are errors, which [syncPurchases] logs and passes over.
 */
internal suspend fun ApphudInternal.syncPurchasesReportingFailures(): ApphudPurchasesRestoreResult =
    performSync(null, null, observerMode = true, unvalidatedPurchs = null, restore = false, reportFailures = true)

private suspend fun ApphudInternal.performSync(
    paywallIdentifier: String?,
    placementIdentifier: String?,
    observerMode: Boolean,
    unvalidatedPurchs: List<Purchase>?,
    restore: Boolean,
    reportFailures: Boolean,
): ApphudPurchasesRestoreResult {
    runCatchingCancellable { awaitUserRegistration() }
        .onFailure { error ->
            ApphudLog.log("SyncPurchases: awaitUserRegistration fail")
            return ApphudPurchasesRestoreResult.Error(error.toApphudError())
        }

    return mutexSync.withLock {
        ApphudLog.log("SyncPurchases: start")

        // What kept a purchase from being read from Google Play. The rest is still sent.
        var billingError: ApphudError? = null
        val neededSync = reportFailures && storage.isNeedSync

        val purchases: List<Purchase> = if (!unvalidatedPurchs.isNullOrEmpty()) {
            unvalidatedPurchs
        } else {
            val result = billing.queryPurchasesSync()
            if (result.first == null || result.second != BillingClient.BillingResponseCode.OK) {
                billingError = ApphudError("Unable to read the purchases from Google Play", errorCode = result.second)
            }
            result.first ?: emptyList()
        }

        if (purchases.isEmpty()) {
            ApphudLog.log(message = "SyncPurchases: Nothing to restore")
            billingError?.takeIf { reportFailures }?.let { return ApphudPurchasesRestoreResult.Error(it) }
            return refreshedPurchases(reportFailures)
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

                // Each type's query fails for products of the other one: only a product neither found is missing.
                val unresolved = purchasesToLoadDetails.flatMap { it.products }.distinct() -
                    restoredPurchases.map { it.details.productId }.toSet()
                if (unresolved.isNotEmpty() && billingError == null) {
                    billingError = ApphudError("Unable to load product details from Google Play for: ${unresolved.joinToString()}")
                }
            } else {
                ApphudLog.log("SyncPurchases: All products details already loaded.")
            }

            ApphudLog.log("Start syncing ${restoredPurchases.count()} in-app purchases: ${restoredPurchases.map { it.details.productId }}")

            val reportedError = billingError?.takeIf { reportFailures }
            if (restoredPurchases.isEmpty() && reportedError != null) {
                return ApphudPurchasesRestoreResult.Error(reportedError)
            }

            val result = if (skipsSync(restore, prevPurchases, restoredPurchases, identityVersion1 = isIdentityV1)) {
                ApphudLog.log("SyncPurchases: Don't send equal purchases from prev state")
                refreshedPurchases(reportFailures)
            } else {
                sendPurchasesToApphud(
                    paywallIdentifier = paywallIdentifier,
                    placementIdentifier = placementIdentifier,
                    tempPurchaseRecordDetails = restoredPurchases,
                    observerMode = observerMode,
                    restore = restore,
                )
            }
            if (reportedError == null) return@withLock result

            // Sending the rest cleared the flag: a sync that was needed still is. Never raise it here, as
            // the recovery it starts uploads outside observer mode.
            if (neededSync) storage.isNeedSync = true
            if (result is ApphudPurchasesRestoreResult.Success) ApphudPurchasesRestoreResult.Error(reportedError) else result
        }
    }
}

/** Nothing to send: the answer is the user, refreshed from the backend. */
private suspend fun ApphudInternal.refreshedPurchases(reportFailures: Boolean): ApphudPurchasesRestoreResult {
    storage.isNeedSync = false
    if (refreshEntitlements(true) == null && reportFailures) {
        return ApphudPurchasesRestoreResult.Error(ApphudError("Unable to refresh the user from Apphud"))
    }
    val user = userRepository.getCurrentUser()
    return if (user != null) {
        ApphudPurchasesRestoreResult.Success(user.subscriptions.toList(), user.purchases.toList())
    } else {
        ApphudPurchasesRestoreResult.Error(ApphudError("User not found."))
    }
}

internal suspend fun ApphudInternal.sendPurchasesToApphud(
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    tempPurchaseRecordDetails: List<PurchaseRecordDetails>?,
    observerMode: Boolean,
    restore: Boolean = false,
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
            restore,
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
