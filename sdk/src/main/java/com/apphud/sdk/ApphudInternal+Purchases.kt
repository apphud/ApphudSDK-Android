package com.apphud.sdk

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.callback_status.PurchaseCallbackStatus
import com.apphud.sdk.internal.callback_status.PurchaseUpdatedCallbackStatus
import com.apphud.sdk.managers.RequestManager
import com.apphud.sdk.storage.SharedPreferencesStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun ApphudInternal.purchase(
    activity: Activity,
    apphudProduct: ApphudProduct?,
    productId: String?,
    offerIdToken: String?,
    oldToken: String?,
    replacementMode: Int?,
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
                val products = productGroups.map { it.products ?: listOf() }.flatten().distinctBy { it.id }
                products.firstOrNull { it.productId == pId }
            }

    productToPurchase?.let { product ->
        var details = product.productDetails
        if (details == null) {
            details = getProductDetailsByProductId(product.productId)
        }

        details?.let {
            if (details.productType == BillingClient.ProductType.SUBS) {
                offerIdToken?.let {
                    purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
                } ?: run {
                    val firstOfferToken = it.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    purchaseInternal(activity, product, firstOfferToken, oldToken, replacementMode, consumableInappProduct, callback)
                    ApphudLog.logE("OfferToken not set. You are required to pass offer token in Apphud.purchase method when purchasing subscription. Passing first offerToken as a fallback.")
//                    callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("OfferToken required")))
                }
            } else {
                purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
            }
        } ?: run {
            coroutineScope.launch(errorHandler) {
                fetchDetailsAndPurchase(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
            }
        }
    } ?: run {
        val id = productId ?: apphudProduct?.productId ?: ""
        callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("Apphud product not found: $id")))
    }
}

private suspend fun ApphudInternal.fetchDetailsAndPurchase(
    activity: Activity,
    apphudProduct: ApphudProduct,
    offerIdToken: String?,
    oldToken: String?,
    prorationMode: Int?,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    val responseCode = fetchDetails(listOf(apphudProduct.productId))
    val productDetails = getProductDetailsByProductId(apphudProduct.productId)
    if (productDetails != null) {
        mainScope.launch {
            apphudProduct.productDetails = productDetails
            purchaseInternal(activity, apphudProduct, offerIdToken, oldToken, prorationMode, consumableInappProduct, callback)
        }
    } else {
        val message = "[${ApphudBillingResponseCodes.getName(responseCode)}] Aborting purchase because product unavailable: ${apphudProduct.productId}"
        ApphudLog.log(message = message, sendLogToServer = true)
        mainScope.launch {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message, errorCode = responseCode)))
        }
    }
}

private fun ApphudInternal.purchaseInternal(
    activity: Activity,
    apphudProduct: ApphudProduct,
    offerIdToken: String?,
    oldToken: String?,
    replacementMode: Int?,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    billing.acknowledgeCallback = { status, purchase ->
        billing.acknowledgeCallback = null
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Sending to server, but failed to acknowledge purchase with code: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                    sendCheckToApphud(purchase, apphudProduct, offerIdToken, oldToken, callback)
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully acknowledged")
                    sendCheckToApphud(purchase, apphudProduct, offerIdToken, oldToken, callback)
                }
            }
        }
    }
    billing.consumeCallback = { status, purchase ->
        billing.consumeCallback = null
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Sending to server, but failed to consume purchase with error: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                    sendCheckToApphud(purchase, apphudProduct, offerIdToken, oldToken, callback)
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully consumed: ${status.message}")
                    sendCheckToApphud(purchase, apphudProduct, offerIdToken, oldToken, callback)
                }
            }
        }
    }
    billing.purchasesCallback = { purchasesResult ->
        mainScope.launch {
            when (purchasesResult) {
                is PurchaseUpdatedCallbackStatus.Error -> {
                    val message =
                        apphudProduct.productDetails?.let {
                            "Unable to buy product with given product id: ${it.productId} "
                        } ?: run {
                            "Unable to buy product with given product id: ${apphudProduct.productId} "
                        }

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
                    billing.purchasesCallback = null
                }

                is PurchaseUpdatedCallbackStatus.Success -> {
                    ApphudLog.log("purchases success: $purchasesResult")

                    val detailsType =
                        apphudProduct.productDetails?.productType ?: run {
                            apphudProduct.productDetails?.productType
                        }

                    purchasesResult.purchases.forEach {
                        when (it.purchaseState) {
                            Purchase.PurchaseState.PENDING -> {
                                val error = ApphudError("Purchase is pending. Please finish the payment.", null, APPHUD_PURCHASE_PENDING)
                                ApphudLog.log("Purchase Pending")
                                callback?.invoke(ApphudPurchaseResult(null, null, it, error))
                                storage.isNeedSync = true
                            }
                            Purchase.PurchaseState.PURCHASED -> {
                                billing.purchasesCallback = null
                                when (detailsType) {
                                    BillingClient.ProductType.SUBS -> {
                                        if (!it.isAcknowledged) {
                                            ApphudLog.log("Start subs purchase acknowledge")
                                            billing.acknowledge(it)
                                        } else {
                                            sendCheckToApphud(
                                                it,
                                                apphudProduct,
                                                offerIdToken,
                                                oldToken,
                                                callback
                                            )
                                        }
                                    }

                                    BillingClient.ProductType.INAPP -> {
                                        if (consumableInappProduct) {
                                            ApphudLog.log("Start inapp consume purchase")
                                            billing.consume(it)
                                        } else {
                                            ApphudLog.log("Start inapp purchase acknowledge")
                                            billing.acknowledge(it)
                                        }
                                    }

                                    else -> {
                                        val message = "Invalid Product Type"
                                        ApphudLog.log(message)
                                        callback?.invoke(
                                            ApphudPurchaseResult(
                                                null,
                                                null,
                                                it,
                                                ApphudError(message)
                                            )
                                        )
                                    }
                                }
                            } else -> {
                                billing.purchasesCallback = null
                                val message = "Error: unknown purchase state. Please try again."
                                ApphudLog.log(message = message)
                                callback?.invoke(ApphudPurchaseResult(null, null, it, ApphudError(message)))
                            }
                        }
                    }
                }
            }
        }
    }

    apphudProduct.productDetails?.let {
        paywallCheckoutInitiated(apphudProduct.paywallId, apphudProduct.placementId, apphudProduct.productId)
        purchasingProduct = apphudProduct
        billing.purchase(
            activity, it, offerIdToken, oldToken, replacementMode,
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

internal fun ApphudInternal.handlePurchaseWithoutCallbacks(purchase: Purchase) {
    val productId = purchase.products.first()

    val products = paywalls.map { it.products ?: listOf() }.flatten().distinctBy { it.id }
    var apphudProduct = purchasingProduct ?: products.firstOrNull { it.productId == productId }
    if (apphudProduct == null) {
        val groupProducts = productGroups.map { it.products ?: listOf() }.flatten().distinctBy { it.id }
        apphudProduct = groupProducts.firstOrNull { it.productId == productId }
    }

    ApphudLog.log("Handle purchase without callback: ${purchase.products}, product: ${apphudProduct}")

    apphudProduct?.let {
        sendCheckToApphud(purchase, apphudProduct, null, null) {}
    }
}

private fun ApphudInternal.processPurchaseError(status: PurchaseUpdatedCallbackStatus.Error) {
    if (status.result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
        storage.isNeedSync = true
        coroutineScope.launch(errorHandler) {
            ApphudLog.log("ProcessPurchaseError: syncPurchases()")
            fetchNativePurchases(forceRefresh = true)
        }
    }
}

private fun ApphudInternal.sendCheckToApphud(
    purchase: Purchase,
    apphudProduct: ApphudProduct,
    offerIdToken: String?,
    oldToken: String?,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    performWhenUserRegistered { error ->

        error?.let {
            ApphudLog.logE(it.message)
            if (fallbackMode) {
                currentUser?.let {
                    coroutineScope.launch(errorHandler) {
                        RequestManager.purchased(purchase, apphudProduct, offerIdToken, oldToken) { _, _ -> }
                    }
                    mainScope.launch {
                        addTempPurchase(
                            it,
                            purchase,
                            apphudProduct.productDetails?.productType ?: "",
                            apphudProduct.productId,
                            callback,
                        )
                    }
                }
            } else {
                storage.isNeedSync = true
            }
        } ?: run {
            coroutineScope.launch(errorHandler) {
                RequestManager.purchased(purchase, apphudProduct, offerIdToken, oldToken) { customer, error ->
                    mainScope.launch {
                        customer?.let {
                            val newSubscriptions = customer.subscriptions.firstOrNull { it.productId == purchase.products.first() }
                            val newPurchases = customer.purchases.firstOrNull { it.productId == purchase.products.first() }

                            notifyAboutSuccess(it, purchase, newSubscriptions, newPurchases, false, callback)
                        }
                        error?.let {
                            if (fallbackMode) {
                                it.errorCode?.let { code ->
                                    if (code in FALLBACK_ERRORS) {
                                        currentUser?.let {
                                            addTempPurchase(it, purchase, apphudProduct.productDetails?.productType ?: "", apphudProduct.productId, callback)
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
        BillingClient.ProductType.SUBS -> {
            newSubscription = ApphudSubscription.createTemporary(productId)
            val mutableSubs = currentUser?.subscriptions?.toMutableList() ?: mutableListOf()
            newSubscription.let {
                mutableSubs.add(it)
                currentUser?.subscriptions = mutableSubs
                ApphudLog.log("Fallback: created temp SUBS purchase: $productId")
            }
        }
        BillingClient.ProductType.INAPP -> {
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
    storage.isNeedSync = true
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
    ApphudInternal.purchasingProduct = null
    ApphudInternal.billing.purchasesCallback = null
    if (newSubscription == null && newPurchase == null) {
        val productId = purchase.products.first() ?: "unknown"
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
    productDetails: ProductDetails,
    offerIdToken: String?,
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
                    offerIdToken,
                    null,
                    true,
                )
            }
        }
    }
}
