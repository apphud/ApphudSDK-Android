package com.apphud.sdk.internal.data.remote

import com.apphud.sdk.ApphudError
import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.body.PurchaseItemBody
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ProductInfo
import com.apphud.sdk.domain.PurchaseRecordDetails
import com.apphud.sdk.internal.data.UserRepository
import com.apphud.sdk.internal.domain.model.PurchaseContext
import com.apphud.sdk.managers.RequestManager.BILLING_VERSION
import com.apphud.sdk.managers.priceAmountMicros
import com.apphud.sdk.managers.priceCurrencyCode
import com.apphud.sdk.managers.subscriptionPeriod

internal class PurchaseBodyFactory(
    private val userRepository: UserRepository,
    private val legacyDeviceId: () -> String? = { null },
) {

    private fun ids() = userRepository.getRequestIds() ?: throw ApphudError("SDK not initialized")

    /**
     * The ids of a purchase body. Identity version 1 sends `device_id` only, as SDK 3.6 did; version 2
     * sends [restore] and the old SDK's device id too.
     */
    private fun body(restore: Boolean, purchases: List<PurchaseItemBody>): PurchaseBody {
        val ids = ids()
        return PurchaseBody(
            deviceId = ids.deviceId,
            installationId = ids.installationId,
            anonymousId = ids.anonymousId,
            userId = ids.identifiedUserId,
            restore = restore.takeUnless { ids.isVersion1 },
            legacyDeviceId = legacyDeviceId().takeUnless { ids.isVersion1 },
            purchases = purchases,
        )
    }

    /** A purchase the app just made: never a restore. */
    fun create(purchaseContext: PurchaseContext): PurchaseBody =
        body(
            restore = false,
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
                        variationIdentifier = variationIdentifier(placementId),
                        screenId = screenId,
                        ruleId = ruleId,
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

    /** The device's purchases: [restore] when the user asked for a restore, otherwise an automatic sync. */
    fun create(
        apphudProduct: ApphudProduct?,
        purchases: List<PurchaseRecordDetails>,
        observerMode: Boolean,
        restore: Boolean = false,
    ): PurchaseBody =
        body(
            restore = restore,
            purchases =
                purchases.map { item ->
                    val matchesApphudProduct =
                        apphudProduct?.productDetails?.productId == item.details.productId
                    val placementId =
                        if (matchesApphudProduct) {
                            apphudProduct.placementId
                        } else {
                            null
                        }
                    PurchaseItemBody(
                        orderId = null,
                        productId = item.details.productId,
                        purchaseToken = item.purchase.purchaseToken,
                        priceCurrencyCode = item.details.priceCurrencyCode(),
                        priceAmountMicros =
                            if ((System.currentTimeMillis() - item.purchase.purchaseTime) < ONE_HOUR) {
                                item.details.priceAmountMicros()
                            } else {
                                null
                            },
                        subscriptionPeriod = item.details.subscriptionPeriod(),
                        screenId = null,
                        paywallId = if (matchesApphudProduct) {
                            apphudProduct.paywallId
                        } else {
                            null
                        },
                        placementId = placementId,
                        variationIdentifier = variationIdentifier(placementId),
                        productBundleId = if (matchesApphudProduct) {
                            apphudProduct.id
                        } else {
                            null
                        },
                        observerMode = observerMode,
                        billingVersion = BILLING_VERSION,
                        purchaseTime = item.purchase.purchaseTime,
                        productInfo = null,
                        productType = item.details.productType,
                        timestamp = System.currentTimeMillis(),
                        extraMessage = null
                    )
                }.sortedByDescending { it.purchaseTime },
        )

    private fun variationIdentifier(placementId: String?): String? {
        if (placementId == null) return null
        return userRepository.getCurrentUser()
            ?.placements
            ?.firstOrNull { it.id == placementId }
            ?.paywall
            ?.variationIdentifier
    }

    private companion object {
        const val ONE_HOUR = 3600_000L
    }
}
