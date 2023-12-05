package com.apphud.sdk

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.Customer
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
    if (apphudProduct == null && productId.isNullOrEmpty())
        {
            callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("Invalid parameters")))
            return
        }

    val productToPurchase =
        apphudProduct
            ?:
            productId?.let { pId ->
                val products = paywalls.map { it.products ?: listOf() }.flatten().distinctBy { it.id }
                products.firstOrNull { it.product_id == pId }
            }

    productToPurchase?.let { product ->
        var details = product.productDetails
        if (details == null)
            {
                details = getProductDetailsByProductId(product.product_id)
            }

        details?.let {
            if (details.productType == BillingClient.ProductType.SUBS)
                {
                    offerIdToken?.let {
                        purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
                    } ?: run {
                        callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("OfferToken required")))
                    }
                } else
                {
                    purchaseInternal(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
                }
        } ?: run {
            coroutineScope.launch(errorHandler) {
                fetchDetails(activity, product, offerIdToken, oldToken, replacementMode, consumableInappProduct, callback)
            }
        }
    } ?: run {
        val id = productId ?: apphudProduct?.product_id ?: ""
        callback?.invoke(ApphudPurchaseResult(null, null, null, ApphudError("Appphud product not found: $id")))
    }
}

private suspend fun ApphudInternal.fetchDetails(
    activity: Activity,
    apphudProduct: ApphudProduct,
    offerIdToken: String?,
    oldToken: String?,
    prorationMode: Int?,
    consumableInappProduct: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    val productName: String = apphudProduct.product_id
    if (loadDetails(productName, apphudProduct))
        {
            getProductDetailsByProductId(productName)?.let { details ->
                mainScope.launch {
                    apphudProduct.productDetails = details
                    purchaseInternal(activity, apphudProduct, offerIdToken, oldToken, prorationMode, consumableInappProduct, callback)
                }
            }
        } else
        {
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
    val productName: String = productId ?: apphudProduct?.product_id!!
    ApphudLog.log("Could not find Product for product id: $productName in memory")
    ApphudLog.log("Now try fetch it from Google Billing")

    return coroutineScope {
        var isInapLoaded = false
        var isSubsLoaded = false

        val subs = async { billing.detailsEx(BillingClient.ProductType.SUBS, listOf(productName)) }
        val inap = async { billing.detailsEx(BillingClient.ProductType.INAPP, listOf(productName)) }

        subs.await()?.let {
            productDetails.addAll(it)
            isSubsLoaded = true

            ApphudLog.log("Google Billing return this info for product id = $productName :")
            it.forEach { ApphudLog.log("$it") }
        } ?: run {
            ApphudLog.logE("Unable to load SUBS details")
        }

        inap.await()?.let {
            productDetails.addAll(it)
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
                    val message = "Failed to acknowledge purchase with code: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null, null, purchase, ApphudError(message)))
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
                    val message = "Failed to consume purchase with error: ${status.error}" + apphudProduct?.let { " [Apphud product ID: " + it.id + "]" }
                    ApphudLog.log(message = message, sendLogToServer = true)
                    callback?.invoke(ApphudPurchaseResult(null, null, purchase, ApphudError(message)))
                }
                is PurchaseCallbackStatus.Success -> {
                    ApphudLog.log("Purchase successfully consumed: ${status.message}")
                    sendCheckToApphud(purchase, apphudProduct, offerIdToken, oldToken, callback)
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
                        apphudProduct.productDetails?.let {
                            "Unable to buy product with given product id: ${it.productId} "
                        } ?: run {
                            paywallPaymentCancelled(apphudProduct.paywall_id, apphudProduct.product_id, purchasesResult.result.responseCode)
                            "Unable to buy product with given product id: ${apphudProduct.productDetails?.productId} "
                        }

                    message += " [Apphud product ID: " + apphudProduct.id + "]"

                    val error =
                        ApphudError(
                            message = message,
                            secondErrorMessage = purchasesResult.result.debugMessage,
                            errorCode = purchasesResult.result.responseCode,
                        )
                    ApphudLog.log(message = error.toString())
                    callback?.invoke(ApphudPurchaseResult(null, null, null, error))
                    processPurchaseError(purchasesResult)
                }

                is PurchaseUpdatedCallbackStatus.Success -> {
                    ApphudLog.log("purchases: $purchasesResult")

                    val detailsType =
                        apphudProduct.productDetails?.productType ?: run {
                            apphudProduct.productDetails?.productType
                        }

                    purchasesResult.purchases.forEach {
                        when (it.purchaseState) {
                            Purchase.PurchaseState.PURCHASED ->
                                when (detailsType) {
                                    BillingClient.ProductType.SUBS -> {
                                        if (!it.isAcknowledged) {
                                            ApphudLog.log("Start subs purchase acknowledge")
                                            billing.acknowledge(it)
                                        }
                                    }
                                    BillingClient.ProductType.INAPP -> {
                                        if (consumableInappProduct)
                                            {
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

    apphudProduct.productDetails?.let {
        paywallCheckoutInitiated(apphudProduct.paywall_id, apphudProduct.product_id)
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

private fun ApphudInternal.processPurchaseError(status: PurchaseUpdatedCallbackStatus.Error)  {
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
    offerIdToken: String?,
    oldToken: String?,
    callback: ((ApphudPurchaseResult) -> Unit)?,
) {
    checkRegistration { error ->
        error?.let {
            ApphudLog.logE(it.message)
            if (fallbackMode)
                {
                    currentUser?.let {
                        addTempPurchase(it, purchase, apphudProduct.productDetails?.productType ?: "", apphudProduct.product_id, callback)
                    }
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
                            if (fallbackMode)
                                {
                                    it.errorCode?.let { code ->
                                        if (code in FALLBACK_ERRORS)
                                            {
                                                currentUser?.let {
                                                    addTempPurchase(it, purchase, apphudProduct.productDetails?.productType ?: "", apphudProduct.product_id, callback)
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
    customer: Customer,
    purchase: Purchase,
    type: String,
    productId: String,
    callback: (
        (ApphudPurchaseResult) -> Unit
    )?,
)  {
    var newSubscriptions: ApphudSubscription? = null
    var newPurchases: ApphudNonRenewingPurchase? = null
    when (type) {
        BillingClient.ProductType.SUBS -> {
            newSubscriptions = ApphudSubscription.createTemporary(productId)
            currentUser?.subscriptions?.add(newSubscriptions)
            ApphudLog.log("Fallback: created temp SUBS purchase: $productId")
        }
        BillingClient.ProductType.INAPP -> {
            newPurchases = ApphudNonRenewingPurchase.createTemporary(productId)
            currentUser?.purchases?.add(newPurchases)
            ApphudLog.log("Fallback: created temp INAPP purchase: $productId")
        }
        else -> {
            // nothing
        }
    }
    notifyAboutSuccess(customer, purchase, newSubscriptions, newPurchases, true, callback)
}

private fun notifyAboutSuccess(
    customer: Customer,
    purchase: Purchase,
    newSubscription: ApphudSubscription?,
    newPurchase: ApphudNonRenewingPurchase?,
    fromFallback: Boolean,
    callback: ((ApphudPurchaseResult) -> Unit)?,
)  {
    ApphudInternal.notifyLoadingCompleted(customerLoaded = customer, fromFallback = fromFallback)

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
) {
    checkRegistration { error ->
        error?.let {
            ApphudLog.logE(it.message)
        } ?: run {
            ApphudLog.log("TrackPurchase()")
            coroutineScope.launch(errorHandler) {
                sendPurchasesToApphud(
                    paywallIdentifier,
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
