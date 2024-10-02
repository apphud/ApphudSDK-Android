package com.apphud.sdk

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.PurchasesUpdatedCallback
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
            product.productDetails = details
        }

        details?.let {
            if (details.productType == BillingClient.ProductType.SUBS) {
                offerIdToken?.let {
                    purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
                } ?: run {
                    val firstOfferToken = it.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    purchaseInternal(activity, product, firstOfferToken, oldToken, replacementMode, consumableInappProduct, callback)
                    ApphudLog.logE("OfferToken not set. You are required to pass offer token in Apphud.purchase method when purchasing subscription. Passing first offerToken as a fallback.")
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
        val message = "[${ApphudBillingResponseCodes.getName(responseCode.first)}] Aborting purchase because product unavailable: ${apphudProduct.productId}"
        ApphudLog.log(message = message, sendLogToServer = true)
        mainScope.launch {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message, errorCode = responseCode.first)))
        }
    }
}

var purchaseStartedAt: Long = 0
private fun ApphudInternal.purchaseInternal(
    activity: Activity,
    apphudProduct: ApphudProduct,
    offerIdToken: String?,
    oldToken: String?,
    replacementMode: Int?,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    billing.purchasesCallback?.let {
        val purchaseDuration = (System.currentTimeMillis() - purchaseStartedAt)
        if (purchaseDuration < 1000) {
            val message = "Purchase flow just started, aborting duplicate method call."
            ApphudLog.logE(message = message)
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
            return
        }
    }

    apphudProduct.productDetails?.let {
        paywallCheckoutInitiated(apphudProduct.paywallId, apphudProduct.placementId, apphudProduct.productId)
        purchasingProduct = apphudProduct
        purchaseStartedAt = System.currentTimeMillis()
        coroutineScope.launch(errorHandler) {
            billing.purchasesCallback = { purchasesResult ->
                mainScope.launch {
                    billing.purchasesCallback = null
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
                        }

                        is PurchaseUpdatedCallbackStatus.Success -> {
                            ApphudLog.log("purchases success: $purchasesResult")

                            val detailsType =
                                apphudProduct.productDetails?.productType ?: run {
                                    apphudProduct.productDetails?.productType
                                }

                            purchasesResult.purchases.forEach { purchase ->
                                when (purchase.purchaseState) {
                                    Purchase.PurchaseState.PENDING -> {
                                        val error = ApphudError("Purchase is pending. Please finish the payment.", null, APPHUD_PURCHASE_PENDING)
                                        ApphudLog.log("Purchase Pending")
                                        callback?.invoke(ApphudPurchaseResult(null, null, purchase, error))
                                        storage.isNeedSync = true
                                    }
                                    Purchase.PurchaseState.PURCHASED -> {
                                        val product = apphudProduct.productDetails ?: getProductDetailsByProductId((purchase.products.firstOrNull() ?: ""))
                                        sendCheckToApphud(purchase, apphudProduct, product, apphudProduct.paywallId, apphudProduct.placementId, offerIdToken, oldToken, callback)

                                        when (detailsType) {
                                            BillingClient.ProductType.SUBS -> {
                                                if (!purchase.isAcknowledged) {
                                                    handlePurchaseAcknowledgment(purchase, apphudProduct, "subs")
                                                }
                                            }
                                            BillingClient.ProductType.INAPP -> {
                                                if (consumableInappProduct) {
                                                    handlePurchaseConsumption(purchase, apphudProduct)
                                                } else {
                                                    handlePurchaseAcknowledgment(purchase, apphudProduct, "inapp")
                                                }
                                            }
                                        }
                                    } else -> {
                                        val message = "Error: unknown purchase state. Please try again."
                                        ApphudLog.log(message = message)
                                        callback?.invoke(ApphudPurchaseResult(null, null, purchase, ApphudError(message)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val error = billing.purchase(activity, it, offerIdToken, oldToken, replacementMode, deviceId)
            if (error != null) {
                billing.purchasesCallback = null
                purchasingProduct = null
                mainScope.launch {
                    callback?.invoke(ApphudPurchaseResult(null, null, null, error))
                }
            }
        }
    } ?: run {
        val message = "Unable to buy product with because ProductDetails is null [Apphud product ID: ${apphudProduct.id}]"
        ApphudLog.log(message = message)
        mainScope.launch {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
        }
    }
}

private suspend fun ApphudInternal.handlePurchaseAcknowledgment(purchase: Purchase, apphudProduct: ApphudProduct?, productType: String) {
    ApphudLog.log("Start $productType purchase acknowledge")
    billing.acknowledge(purchase) { status, _ ->
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Sending to server, but failed to acknowledge purchase with code: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully acknowledged")
                }
            }
        }
    }
}

private suspend fun ApphudInternal.handlePurchaseConsumption(purchase: Purchase, apphudProduct: ApphudProduct?) {
    ApphudLog.log("Start inapp consume purchase")
    billing.consume(purchase) { status, _ ->
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message = "Sending to server, but failed to consume purchase with error: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully consumed: ${status.message}")
                }
            }
        }
    }
}

internal fun ApphudInternal.handleObservedPurchase(purchase: Purchase, userInitiated: Boolean, paywallIdentifier: String? = null, placementIdentifier: String? = null, offerIdToken: String? = null) {
    val productId = purchase.products.first()
    ApphudLog.log("Observed Purchase: ${purchase.products} User Initiated: $userInitiated")

    if (purchasingProduct?.productId != productId) {
        purchasingProduct = null
    }

    coroutineScope.launch {
        if (!userInitiated) {  Thread.sleep(1000) }
        var productDetails = purchasingProduct?.productDetails ?: getProductDetailsByProductId(productId)
        if (productDetails == null) {
            val response = fetchDetails(listOf(productId))
            productDetails = response.second?.find { it.productId == productId }
        }
        mainScope.launch {
            sendCheckToApphud(purchase,
                purchasingProduct,
                purchasingProduct?.productDetails ?: productDetails,
                paywallIdentifier ?: purchasingProduct?.paywallId,
                placementIdentifier ?: purchasingProduct?.placementId, offerIdToken, null, callback = null)
        }
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

private fun rememberCallback(callback: (ApphudPurchaseResult) -> Unit) {
    synchronized(ApphudInternal.purchaseCallbacks) {
        ApphudInternal.purchaseCallbacks.add(callback)
    }
}

private fun ApphudInternal.sendCheckToApphud(
    purchase: Purchase,
    apphudProduct: ApphudProduct?,
    productDetails: ProductDetails?,
    paywallId: String?,
    placementId: String?,
    offerIdToken: String?,
    oldToken: String?,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    performWhenUserRegistered { error ->

        error?.let { it ->
            ApphudLog.logE(it.message)
            if (fallbackMode) {
                currentUser?.let { user ->
                    coroutineScope.launch(errorHandler) {
                        RequestManager.purchased(purchase, productDetails, apphudProduct?.id, paywallId, placementId, offerIdToken, oldToken) { _, _ -> }
                    }
                    mainScope.launch {

                        callback?.let { rememberCallback(callback) }

                        addTempPurchase(user, purchase,
                            apphudProduct?.productDetails?.productType ?: productDetails?.productType ?: "",
                            apphudProduct?.productId ?: productDetails?.productId ?: "")
                    }
                }
            } else {
                storage.isNeedSync = true
            }
        } ?: run {
            coroutineScope.launch(errorHandler) {

                callback?.let { rememberCallback(callback) }

                synchronized(observedOrders) {
                    purchase.orderId?.let {
                        if (observedOrders.contains(it) && callback == null) {
                            ApphudLog.logI("Already observed order ${it}, skipping...")
                            return@launch
                        } else {
                            observedOrders.add(it)
                        }
                    }
                }

                RequestManager.purchased(purchase, productDetails, apphudProduct?.id, paywallId, placementId, offerIdToken, oldToken) { customer, error ->
                    mainScope.launch {
                        customer?.let {
                            val newSubscriptions = customer.subscriptions.firstOrNull { it.productId == purchase.products.first() }
                            val newPurchases = customer.purchases.firstOrNull { it.productId == purchase.products.first() }

                            handleCheckSubmissionResult(it, purchase, newSubscriptions, newPurchases, false)
                        }
                        error?.let {
                            if (fallbackMode) {
                                it.errorCode?.let { code ->
                                    if (code in FALLBACK_ERRORS) {
                                        currentUser?.let {
                                            apphudProduct?.let { product ->
                                                addTempPurchase(it, purchase, product.productDetails?.productType ?: "", product.productId)
                                            }?: run {
                                                productDetails?.let{ details ->
                                                    addTempPurchase(it, purchase, details.productType, details.productId)
                                                }
                                            }
                                            return@launch
                                        }
                                    }
                                }
                            }

                            handleCheckSubmissionResult(customer, purchase, null, null, false)
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
    productId: String) {
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
    handleCheckSubmissionResult(apphudUser, purchase, newSubscription, newPurchase, true)
}

private fun handleCheckSubmissionResult(
    apphudUser: ApphudUser?,
    purchase: Purchase,
    newSubscription: ApphudSubscription?,
    newPurchase: ApphudNonRenewingPurchase?,
    fromFallback: Boolean,
) {
    if (apphudUser != null) {
        ApphudInternal.notifyLoadingCompleted(
            customerLoaded = apphudUser,
            fromFallback = fromFallback
        )
    }

    ApphudInternal.purchasingProduct = null
    var error: ApphudError? = null
    if (newSubscription == null && newPurchase == null) {
        val productId = purchase.products.first() ?: "unknown"
        val message =
            "Unable to validate purchase ($productId). " +
                    "Ensure Google Service Credentials are correct and have necessary permissions. " +
                    "Check https://docs.apphud.com/getting-started/creating-app#google-play-service-credentials or contact support."

        ApphudLog.logE(message)
        error = ApphudError(message)
    }

    val result = ApphudPurchaseResult(newSubscription, newPurchase, purchase, error)

    val callbacksCopy = ApphudInternal.purchaseCallbacks.toMutableList()

    while (callbacksCopy.isNotEmpty()) {
        val callback = callbacksCopy.removeFirst()
        callback.invoke(result)
    }
    synchronized(ApphudInternal.purchaseCallbacks) {
        ApphudInternal.purchaseCallbacks.clear()
    }
}

internal fun ApphudInternal.trackPurchase(
    productId: String,
    offerIdToken: String?,
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null
) {
    performWhenUserRegistered { error ->
        error?.let {
            ApphudLog.logE(it.message)
        } ?: run {
            coroutineScope.launch(errorHandler) {
                val result = fetchNativePurchases(forceRefresh = true, needSync = false)
                if (result.second == BillingClient.BillingResponseCode.OK) {
                    val purchases = result.first
                    val purchase = purchases.firstOrNull { it.products.contains(productId) }
                    purchase?.let { p ->
                        handleObservedPurchase(p, true, paywallIdentifier, placementIdentifier, offerIdToken)
                    } ?: run {
                        ApphudLog.logE("trackPurchase: could not found purchase for product $productId")
                    }
                } else {
                    storage.isNeedSync = true
                    mainScope.launch {
                        syncPurchases(observerMode = true, callback = null)
                    }
                }
            }
        }
    }
}
