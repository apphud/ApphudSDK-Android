package com.apphud.sdk

import android.app.Activity
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.Purchase
import com.xiaomi.billingclient.api.SkuDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun ApphudInternal.purchase(
    activity: Activity,
    apphudProduct: ApphudProduct?,
    productId: String?,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    if (apphudProduct == null && productId.isNullOrEmpty()) {
        callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("Invalid parameters")))
        return
    }

    val productToPurchase =
        apphudProduct
            ?: productId?.let { pId ->
                val products = paywalls.map { it.products ?: listOf() }.flatten().distinctBy { it.id }
                products.firstOrNull { it.productId == pId }
            }

    productToPurchase?.let { product ->
        var details = product.skuDetails
        if (details == null) {
            details = getSkuDetailsByProductId(product.productId)
        }

        details?.let {
            purchaseInternal(activity, product, consumableInappProduct, callback)
        } ?: run {
            coroutineScope.launch(errorHandler) {
                fetchDetails(activity, product, consumableInappProduct, callback)
            }
        }
    } ?: run {
        val id = productId ?: apphudProduct?.productId ?: ""
        callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("Apphud product not found: $id")))
    }
}

private suspend fun ApphudInternal.fetchDetails(
    activity: Activity,
    apphudProduct: ApphudProduct,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    val productName: String = apphudProduct.productId
    if (loadDetails(productName, apphudProduct)) {
        getSkuDetailsByProductId(productName)?.let { details ->
            mainScope.launch {
                apphudProduct.skuDetails = details
                purchaseInternal(activity, apphudProduct, consumableInappProduct, callback)
            }
        }
    } else {
        val message = "Unable to fetch product with given product id: $productName" + apphudProduct.let { " [Apphud product ID: " + it.id + "]" }
        ApphudLog.log(message = message, sendLogToServer = true)
        mainScope.launch {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
        }
    }
}

private suspend fun ApphudInternal.loadDetails(
    productId: String?,
    apphudProduct: ApphudProduct?,
): Boolean {
    val productName: String = productId ?: apphudProduct?.productId ?: "none"
    ApphudLog.log("Could not find Product for product id: $productName in memory")
    ApphudLog.log("Now try fetch it from Google Billing")

    return coroutineScope {
        var isInapLoaded = false
        var isSubsLoaded = false

        val subs = async { billing.detailsEx(BillingClient.SkuType.SUBS, listOf(productName)) }
        val inap = async { billing.detailsEx(BillingClient.SkuType.INAPP, listOf(productName)) }

        subs.await()?.let {
            skuDetails.addAll(it)
            isSubsLoaded = true

            ApphudLog.log("Google Billing return this info for product id = $productName :")
            it.forEach { ApphudLog.log("$it") }
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details")
        }

        inap.await()?.let {
            skuDetails.addAll(it)
            isInapLoaded = true
            it.forEach { ApphudLog.log("$it") }
        } ?: run {
            ApphudLog.logE("Unable to load INAP details")
        }
        return@coroutineScope isSubsLoaded && isInapLoaded
    }
}

private fun ApphudInternal.purchaseInternal(
    activity: Activity,
    apphudProduct: ApphudProduct,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    billing.acknowledgeCallback = { status, purchase ->
        billing.acknowledgeCallback = null
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Failed to acknowledge purchase with code: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null, null, purchase, ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully acknowledged")
                    sendCheckToApphud(purchase, apphudProduct, callback)
                }
            }
        }
    }
    billing.consumeCallback = { status, purchase ->
        billing.consumeCallback = null
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Failed to consume purchase with error: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null, null, purchase, ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully consumed: ${status.message}")
                    sendCheckToApphud(purchase, apphudProduct, callback)
                }
            }
        }
    }
    billing.purchasesCallback = { purchasesResult ->
        billing.purchasesCallback = null
        mainScope.launch {
            when (purchasesResult) {
                is PurchaseUpdatedCallbackStatus.Error -> {
                    var message =
                        apphudProduct.skuDetails?.let {
                            "Unable to buy product with given product id: ${it.sku} "
                        } ?: run {
                            "Unable to buy product with given product id: ${apphudProduct.productId} "
                        }

                    message += " [Apphud product ID: " + apphudProduct.id + "]"

                    val error =
                        ApphudError(
                            message = message,
                            secondErrorMessage = purchasesResult.result.debugMessage,
                            errorCode = purchasesResult.result.responseCode,
                        )

                    paywallPaymentCancelled(
                        apphudProduct.paywallId,
                        apphudProduct.placementId,
                        apphudProduct.productId,
                        purchasesResult.result.responseCode,
                    )

                    ApphudLog.log(message = error.toString())
                    callback?.invoke(ApphudPurchaseResult(null, null, null, error))
                    processPurchaseError(purchasesResult)
                }

                is PurchaseUpdatedCallbackStatus.Success -> {
                    ApphudLog.log("purchases: $purchasesResult")

                    val detailsType =
                        apphudProduct.skuDetails?.type ?: run {
                            apphudProduct.skuDetails?.type
                        }

                    purchasesResult.purchases.forEach {
                        when (it.purchaseState) {
                            Purchase.PurchaseState.PURCHASED ->
                                when (detailsType) {
                                    BillingClient.SkuType.SUBS -> {
                                        if (!it.isAcknowledged) {
                                            ApphudLog.log("Start subs purchase acknowledge")
                                            billing.acknowledge(it)
                                        }
                                    }
                                    BillingClient.SkuType.INAPP -> {
                                        if (consumableInappProduct) {
                                            ApphudLog.log("Start inapp consume purchase")
                                            billing.consume(it)
                                        } else {
                                            ApphudLog.log("Start inapp purchase acknowledge")
                                            billing.acknowledge(it)
                                        }
                                    }
                                    else -> {
                                        val message = "After purchase type is null"
                                        ApphudLog.log(message)
                                        callback?.invoke(ApphudPurchaseResult(null, null, it, ApphudError(message)))
                                    }
                                }
                            else -> {
                                val message = "After purchase state: ${it.purchaseState}" + " [Apphud product ID:  ${apphudProduct.id}]"
                                ApphudLog.log(message = message)
                                callback?.invoke(ApphudPurchaseResult(null, null, it, ApphudError(message)))
                            }
                        }
                    }
                }
            }
        }
    }

    apphudProduct.skuDetails?.let {
        paywallCheckoutInitiated(apphudProduct.paywallId, apphudProduct.placementId, apphudProduct.productId)
        billing.purchase(
            activity, it,
            deviceId,
        )
    } ?: run {
        val message = "Unable to buy product with because ProductDetails is null [Apphud product ID: ${apphudProduct.id}]"
        ApphudLog.log(message = message)
        mainScope.launch {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
        }
    }
}

private fun ApphudInternal.processPurchaseError(status: PurchaseUpdatedCallbackStatus.Error) {
    if (status.result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
        SharedPreferencesStorage.isNeedSync = true
        coroutineScope.launch(errorHandler) {
            ApphudLog.log("ProcessPurchaseError: syncPurchases()")
            syncPurchases()
        }
    }
}

private fun ApphudInternal.sendCheckToApphud(
    purchase: Purchase,
    apphudProduct: ApphudProduct,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    performWhenUserRegistered { error ->
        error?.let {
            ApphudLog.logE(it.message)
            if (fallbackMode) {
                currentUser?.let {
                    mainScope.launch {
                        addTempPurchase(
                            it,
                            purchase,
                            apphudProduct.skuDetails?.type ?: "",
                            apphudProduct.productId,
                            callback,
                        )
                    }
                }
            }
        } ?: run {
            coroutineScope.launch(errorHandler) {
                RequestManager.purchased(purchase, apphudProduct) { customer, error ->
                    mainScope.launch {
                        customer?.let {
                            val newSubscriptions = customer.subscriptions.firstOrNull { it.productId == purchase.skus.first() }
                            val newPurchases = customer.purchases.firstOrNull { it.productId == purchase.skus.first() }

                            notifyAboutSuccess(it, purchase, newSubscriptions, newPurchases, false, callback)
                        }
                        error?.let {
                            if (fallbackMode) {
                                it.errorCode?.let { code ->
                                    if (code in FALLBACK_ERRORS) {
                                        currentUser?.let {
                                            addTempPurchase(it, purchase, apphudProduct.skuDetails?.type ?: "", apphudProduct.productId, callback)
                                            return@launch
                                        }
                                    }
                                }
                            }

                            val message = "Unable to validate purchase with error = ${it.message}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                            ApphudLog.logI(message = message)
                            callback?.invoke(ApphudPurchaseResult(null, null, purchase, ApphudError(message)))
                        }
                    }
                }
            }
        }
    }
}

internal fun ApphudInternal.addTempPurchase(
    apphudUser: ApphudUser,
    purchase: Purchase,
    type: String,
    productId: String,
    callback: (
        (ApphudPurchaseResult) -> Unit
    )?,
) {
    var newSubscription: ApphudSubscription? = null
    var newPurchase: ApphudNonRenewingPurchase? = null
    when (type) {
        BillingClient.SkuType.SUBS -> {
            newSubscription = ApphudSubscription.createTemporary(productId)
            val mutableSubs = currentUser?.subscriptions?.toMutableList() ?: mutableListOf()
            newSubscription.let {
                mutableSubs.add(it)
                currentUser?.subscriptions = mutableSubs
                ApphudLog.log("Fallback: created temp SUBS purchase: $productId")
            }
        }
        BillingClient.SkuType.INAPP -> {
            newPurchase = ApphudNonRenewingPurchase.createTemporary(productId)
            val mutablePurchs = currentUser?.purchases?.toMutableList() ?: mutableListOf()
            newPurchase.let {
                mutablePurchs.add(it)
                currentUser?.purchases = mutablePurchs
                ApphudLog.log("Fallback: created temp INAPP purchase: $productId")
            }
        }
        else -> {
            // nothing
        }
    }
    notifyAboutSuccess(apphudUser, purchase, newSubscription, newPurchase, true, callback)
}

private fun notifyAboutSuccess(
    apphudUser: ApphudUser,
    purchase: Purchase,
    newSubscription: ApphudSubscription?,
    newPurchase: ApphudNonRenewingPurchase?,
    fromFallback: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    ApphudInternal.notifyLoadingCompleted(customerLoaded = apphudUser, fromFallback = fromFallback)

    if (newSubscription == null && newPurchase == null) {
        val productId = purchase.skus.first() ?: "unknown"
        val message =
            "Unable to validate purchase ($productId). " +
                "Ensure Google Service Credentials are correct and have necessary permissions. " +
                "Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support."

        ApphudLog.logE(message)
        callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
    } else {
        callback?.invoke(ApphudPurchaseResult(newSubscription, newPurchase, purchase, null))
    }
}

internal fun ApphudInternal.trackPurchase(
    purchase: Purchase,
    productDetails: SkuDetails,
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
) {
    performWhenUserRegistered { error ->
        error?.let {
            ApphudLog.logE(it.message)
        } ?: run {
            ApphudLog.log("TrackPurchase()")
            coroutineScope.launch(errorHandler) {
                sendPurchasesToApphud(
                    paywallIdentifier,
                    placementIdentifier,
                    null,
                    purchase,
                    productDetails,
                    null,
                    true,
                )
            }
        }
    }
}
