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
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.internal.util.runCatchingCancellable
import com.apphud.sdk.managers.RequestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var productToPurchase =
        apphudProduct
            ?: productId?.let { pId ->
                val products = productGroups.map { it.products ?: listOf() }.flatten().distinctBy { it.id }
                products.firstOrNull { it.productId == pId }
            }

    val details = productToPurchase?.productDetails ?: getProductDetailsByProductId(
        productToPurchase?.productId ?: productId ?: "none"
    )

    if (productToPurchase == null && productId != null) {
        productToPurchase = ApphudProduct.apphudProduct(productId)
        productToPurchase.productDetails = details
    }

    productToPurchase?.let { product ->
        details?.let {
            purchaseInternal(
                activity,
                product,
                offerIdToken,
                oldToken,
                replacementMode,
                consumableInappProduct,
                callback
            )
        } ?: run {
            coroutineScope.launch(errorHandler) {
                fetchDetailsAndPurchase(
                    activity,
                    product,
                    offerIdToken,
                    oldToken,
                    replacementMode,
                    consumableInappProduct,
                    callback
                )
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
    val productDetails = responseCode.second?.firstOrNull() ?: getProductDetailsByProductId(apphudProduct.productId)
    if (productDetails != null) {
        mainScope.launch {
            apphudProduct.productDetails = productDetails
            purchaseInternal(
                activity,
                apphudProduct,
                offerIdToken,
                oldToken,
                prorationMode,
                consumableInappProduct,
                callback
            )
        }
    } else {
        val message =
            "[${ApphudBillingResponseCodes.getName(responseCode.first)}] Aborting purchase because product unavailable: ${apphudProduct.productId}"
        ApphudLog.log(message = message)
        mainScope.launch {
            callback?.invoke(
                ApphudPurchaseResult(
                    null,
                    null,
                    null,
                    ApphudError(message, errorCode = responseCode.first)
                )
            )
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

        var token = offerIdToken
        if (it.productType == BillingClient.ProductType.SUBS && offerIdToken == null) {
            token = it.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ApphudLog.logE("OfferToken not set. You are required to pass offer token in Apphud.purchase method when purchasing subscription. Passing first offerToken as a fallback.")
        }

        paywallCheckoutInitiated(apphudProduct.paywallId, apphudProduct.placementId, apphudProduct.productId)
        purchasingProduct = apphudProduct
        purchaseStartedAt = System.currentTimeMillis()
        callback?.let { rememberCallback(callback) }

        scheduleLookupPurchase(25000L)

        coroutineScope.launch(errorHandler) {
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
                            synchronized(purchaseCallbacks) {
                                purchaseCallbacks.clear()
                            }
                            purchasingProduct = null
                            freshPurchase = null
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
                                        val error = ApphudError(
                                            "Purchase is pending. Please finish the payment.",
                                            null,
                                            APPHUD_PURCHASE_PENDING
                                        )
                                        ApphudLog.log("Purchase Pending")
                                        callback?.invoke(ApphudPurchaseResult(null, null, purchase, error))
                                        storage.isNeedSync = true
                                    }
                                    Purchase.PurchaseState.PURCHASED -> {
                                        val product = apphudProduct.productDetails
                                            ?: getProductDetailsByProductId((purchase.products.firstOrNull() ?: ""))
                                        sendCheckToApphud(
                                            purchase,
                                            apphudProduct,
                                            product,
                                            apphudProduct.paywallId,
                                            apphudProduct.placementId,
                                            token,
                                            oldToken,
                                            callback
                                        )

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
                                    }
                                    else -> {
                                        val message = "Error: unknown purchase state. Please try again."
                                        ApphudLog.log(message = message)
                                        callback?.invoke(
                                            ApphudPurchaseResult(
                                                null,
                                                null,
                                                purchase,
                                                ApphudError(message)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    billing.purchasesCallback = null
                }
            }
            val error = billing.purchase(activity, it, token, oldToken, replacementMode, deviceId)
            if (error != null) {
                billing.purchasesCallback = null
                purchasingProduct = null
                mainScope.launch {
                    callback?.invoke(ApphudPurchaseResult(null, null, null, error))
                }
            }
        }
    } ?: run {
        val message =
            "Unable to buy product with because ProductDetails is null [Apphud product ID: ${apphudProduct.id}]"
        ApphudLog.log(message = message)
        mainScope.launch {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError(message)))
        }
    }
}

internal fun ApphudInternal.lookupFreshPurchase(extraMessage: String = "resend_fresh_purchase") {
    coroutineScope.launch(errorHandler) {
        val purchase = freshPurchase.let { mFreshPurchase ->
            if (mFreshPurchase == null) {
                val purchases = ApphudInternal
                    .fetchNativePurchases(forceRefresh = true, needSync = false)
                    .first
                    .ifEmpty { return@launch }

                ApphudLog.logE("recover_native_purchases")
                purchases.first()
            } else {
                mFreshPurchase
            }
        }
        if ((purchaseCallbacks.isNotEmpty() && purchasingProduct != null) || storage.isNeedSync) {

            launch(Dispatchers.Main) { apphudListener?.apphudDidReceivePurchase(purchase) }

            ApphudLog.logE("resending fresh purchase ${purchase.orderId}")

            runCatchingCancellable {
                RequestManager.purchased(
                    PurchaseContext(
                        purchase,
                        getProductDetailsByProductId(purchase.products.first()),
                        purchasingProduct?.id,
                        purchasingProduct?.paywallId,
                        purchasingProduct?.placementId,
                        null,
                        null,
                        extraMessage
                    )
                )
            }
                .onSuccess { customer ->
                    withContext(Dispatchers.Main) {
                        val newSubscriptions =
                            customer.subscriptions.firstOrNull { it.productId == purchase.products.first() }
                        val newPurchases =
                            customer.purchases.firstOrNull { it.productId == purchase.products.first() }

                        storage.isNeedSync = false
                        handleCheckSubmissionResult(customer, purchase, newSubscriptions, newPurchases, false)
                    }
                }
        }
    }
}

private suspend fun ApphudInternal.handlePurchaseAcknowledgment(
    purchase: Purchase,
    apphudProduct: ApphudProduct?,
    productType: String,
) {
    ApphudLog.log("Start $productType purchase acknowledge")
    billing.acknowledge(purchase) { status, _ ->
        mainScope.launch {
            when (status) {
                is PurchaseCallbackStatus.Error -> {
                    val message =
                        "Sending to server, but failed to acknowledge purchase with code: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message)
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
                    val message =
                        "Sending to server, but failed to consume purchase with error: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message)
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully consumed: ${status.message}")
                }
            }
        }
    }
}

internal fun ApphudInternal.handleObservedPurchase(
    purchase: Purchase,
    userInitiated: Boolean,
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
    offerIdToken: String? = null,
) {
    val productId = purchase.products.first()
    ApphudLog.log("Observed Purchase: ${purchase.products} User Initiated: $userInitiated")

    if (purchasingProduct?.productId != productId) {
        purchasingProduct = null
    }

    coroutineScope.launch {
        if (!userInitiated) {
            Thread.sleep(1000)
        }
        var productDetails = purchasingProduct?.productDetails ?: getProductDetailsByProductId(productId)
        if (productDetails == null) {
            val response = fetchDetails(listOf(productId))
            productDetails = response.second?.find { it.productId == productId }
        }
        sendCheckToApphud(
            purchase,
            purchasingProduct,
            purchasingProduct?.productDetails ?: productDetails,
            paywallIdentifier ?: purchasingProduct?.paywallId,
            placementIdentifier ?: purchasingProduct?.placementId, offerIdToken, null, callback = null
        )
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

private suspend fun ApphudInternal.sendCheckToApphud(
    purchase: Purchase,
    apphudProduct: ApphudProduct?,
    productDetails: ProductDetails?,
    paywallId: String?,
    placementId: String?,
    offerIdToken: String?,
    oldToken: String?,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    val localCurrentUser = currentUser
    when {
        localCurrentUser == null -> storage.isNeedSync = true
        fallbackMode -> {
            runCatchingCancellable {
                RequestManager.purchased(
                    PurchaseContext(
                        purchase,
                        productDetails,
                        apphudProduct?.id,
                        paywallId,
                        placementId,
                        offerIdToken,
                        oldToken,
                        "fallback_mode"
                    )
                )
            }
            withContext(Dispatchers.Main) {
                addTempPurchase(
                    apphudUser = localCurrentUser, purchase = purchase,
                    type = apphudProduct?.productDetails?.productType ?: productDetails?.productType ?: "",
                    productId = apphudProduct?.productId ?: productDetails?.productId ?: ""
                )
            }
        }
        else -> {
            synchronized(observedOrders) {
                purchase.orderId?.let {
                    if (observedOrders.contains(it) && callback == null) {
                        ApphudLog.logI("Already observed order ${it}, skipping...")
                        return
                    } else {
                        observedOrders.add(it)
                    }
                }
            }
            runCatchingCancellable {
                RequestManager.purchased(
                    PurchaseContext(
                        purchase,
                        productDetails,
                        apphudProduct?.id,
                        paywallId,
                        placementId,
                        offerIdToken,
                        oldToken,
                        null
                    )
                )
            }
                .onSuccess { customer ->
                    withContext(Dispatchers.IO) {
                        val newSubscriptions =
                            customer.subscriptions.firstOrNull { it.productId == purchase.products.first() }
                        val newPurchases =
                            customer.purchases.firstOrNull { it.productId == purchase.products.first() }

                        storage.isNeedSync = false
                        handleCheckSubmissionResult(customer, purchase, newSubscriptions, newPurchases, false)
                    }
                }
                .onFailure { error ->
                    if (fallbackMode && error is ApphudError) {
                        error.errorCode?.let { code ->
                            if (code in FALLBACK_ERRORS) {
                                apphudProduct?.let { product ->
                                    addTempPurchase(
                                        localCurrentUser,
                                        purchase,
                                        product.productDetails?.productType ?: "",
                                        product.productId
                                    )
                                } ?: run {
                                    productDetails?.let { details ->
                                        addTempPurchase(
                                            localCurrentUser,
                                            purchase,
                                            details.productType,
                                            details.productId
                                        )
                                    }
                                }
                                return
                            }
                        }
                    }
                    storage.isNeedSync = true

                    handleCheckSubmissionResult(null, purchase, null, null, false)
                }
        }
    }
}

internal fun ApphudInternal.addTempPurchase(
    apphudUser: ApphudUser,
    purchase: Purchase,
    type: String,
    productId: String,
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
    ApphudInternal.freshPurchase = null
}

internal suspend fun ApphudInternal.trackPurchase(
    productId: String,
    offerIdToken: String?,
    paywallIdentifier: String? = null,
    placementIdentifier: String? = null,
) {
    runCatchingCancellable { awaitUserRegistration() }
        .onFailure { error ->
            ApphudLog.logE(error.message.orEmpty())
            return
        }

    val (purchases, billingResponseCode) = fetchNativePurchases(forceRefresh = true, needSync = false)

    if (billingResponseCode == BillingClient.BillingResponseCode.OK) {
        val purchase = purchases.firstOrNull { it.products.contains(productId) }
        purchase?.let { p ->
            handleObservedPurchase(p, true, paywallIdentifier, placementIdentifier, offerIdToken)
        } ?: run {
            ApphudLog.logE("trackPurchase: could not found purchase for product $productId")
        }
    } else {
        storage.isNeedSync = true
        withContext(Dispatchers.Main) {
            syncPurchases(observerMode = true)
        }
    }
}
