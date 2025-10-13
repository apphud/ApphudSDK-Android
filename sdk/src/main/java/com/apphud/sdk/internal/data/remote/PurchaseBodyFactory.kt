package com.apphud.sdk.internal.data.remote

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.body.PurchaseItemBody
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ProductInfo
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.managers.RequestManager.BILLING_VERSION
import com.apphud.sdk.managers.priceAmountMicros
import com.apphud.sdk.managers.priceCurrencyCode
import com.apphud.sdk.managers.subscriptionPeriod

internal class PurchaseBodyFactory {

    fun create(purchaseContext: PurchaseContext): PurchaseBody =
        PurchaseBody(
            deviceId = ApphudInternal.deviceId,
            purchases = listOf(
                with(purchaseContext) {
                    PurchaseItemBody(
                        orderId = purchase.orderId,
                        productId = productDetails?.productId ?: purchase.products.first(),
                        purchaseToken = purchase.purchaseToken,
                        priceCurrencyCode = productDetails?.priceCurrencyCode(),
                        priceAmountMicros = productDetails?.priceAmountMicros(),
                        subscriptionPeriod = productDetails?.subscriptionPeriod(),
                        paywallId = paywallId,
                        placementId = placementId,
                        screenId = screenId,
                        productBundleId = productBundleId,
                        observerMode = false,
                        billingVersion = BILLING_VERSION,
                        purchaseTime = purchase.purchaseTime,
                        productInfo = productDetails?.let { ProductInfo(productDetails, offerToken) },
                        productType = productDetails?.productType,
                        timestamp = System.currentTimeMillis(),
                        extraMessage = extraMessage
                    )
                },
            )
        )

    fun create(
        apphudProduct: ApphudProduct?,
        purchases: List<PurchaseRecordDetails>,
        observerMode: Boolean,
    ): PurchaseBody =
        PurchaseBody(
            deviceId = ApphudInternal.deviceId,
            purchases =
            purchases.map { purchase ->
                PurchaseItemBody(
                    orderId = null,
                    productId = purchase.details.productId,
                    purchaseToken = purchase.record.purchaseToken,
                    priceCurrencyCode = purchase.details.priceCurrencyCode(),
                    priceAmountMicros =
                    if ((System.currentTimeMillis() - purchase.record.purchaseTime) < ONE_HOUR) {
                        purchase.details.priceAmountMicros()
                    } else {
                        null
                    },
                    subscriptionPeriod = purchase.details.subscriptionPeriod(),
                    screenId = null,
                    paywallId = if (apphudProduct?.productDetails?.productId == purchase.details.productId) {
                        apphudProduct.paywallId
                    } else {
                        null
                    },
                    placementId = if (apphudProduct?.productDetails?.productId == purchase.details.productId) {
                        apphudProduct.placementId
                    } else {
                        null
                    },
                    productBundleId = if (apphudProduct?.productDetails?.productId == purchase.details.productId) {
                        apphudProduct.id
                    } else {
                        null
                    },
                    observerMode = observerMode,
                    billingVersion = BILLING_VERSION,
                    purchaseTime = purchase.record.purchaseTime,
                    productInfo = null,
                    productType = purchase.details.productType,
                    timestamp = System.currentTimeMillis(),
                    extraMessage = null
                )
            }.sortedByDescending { it.purchaseTime },
        )

    private companion object {
        const val ONE_HOUR = 3600_000L
    }
}