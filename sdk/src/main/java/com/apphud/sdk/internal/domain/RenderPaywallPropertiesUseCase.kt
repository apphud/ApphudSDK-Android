package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.internal.domain.model.RenderItem
import com.apphud.sdk.internal.domain.model.RenderItemProductDetails
import com.apphud.sdk.managers.priceCurrencyCode
import java.util.Currency

/**
 * Use case responsible for rendering paywall product properties.
 *
 * This class handles the process of checking if paywall properties need to be rendered
 * and sending them to the backend to replace macros.
 */
internal class RenderPaywallPropertiesUseCase {

    /**
     * Renders paywall properties if needed.
     *
     * @param paywall The paywall whose properties need to be rendered
     */
    suspend operator fun invoke(paywall: ApphudPaywall) {
        try {
            val items = itemsToRender(paywall)
            if (items.isEmpty()) {
                ApphudLog.log("No products macros to render, skipping")
                return
            }

            // TODO: Integrate with backend API when available.
            // For now we just log.
            ApphudLog.log("renderPropertiesIfNeeded: would send ${'$'}{items.size} items to backend")
        } catch (e: Exception) {
            ApphudLog.logE("Error rendering properties for paywall ${paywall.identifier}: ${e.message}")
            throw e
        }
    }

    /**
     * Returns list of items that should be rendered on backend to replace macros.
     * Currently returns an empty list – implementation will be added in future release.
     */
    private fun itemsToRender(paywall: ApphudPaywall): List<RenderItem> {
        val items = mutableListOf<RenderItem>()
        paywall.products?.forEach { product ->
            val productDetails = product.productDetails

            ApphudLog.log("ProductDetails ${product.productDetails}")
            val hasMacros = true
            if (!hasMacros) {
                return@forEach
            }

            val productDetailsData = productDetails?.let { details ->
                val subscriptionOffers = details.subscriptionOfferDetails
                
                if (!subscriptionOffers.isNullOrEmpty()) {
                    val currencyCode = details.priceCurrencyCode() ?: ""
                    val currencySymbol = getCurrencySymbol(currencyCode) ?: ""
                    
                    val lastOffer = subscriptionOffers.last()
                    val lastPhase = lastOffer.pricingPhases.pricingPhaseList.lastOrNull()
                    val formattedPrice = lastPhase?.formattedPrice ?: ""
                    val price = lastPhase?.let { it.priceAmountMicros / 1_000_000.0 } ?: 0.0
                    
                    val firstOffer = subscriptionOffers.first()
                    val firstPhase = firstOffer.pricingPhases.pricingPhaseList.firstOrNull()
                    val (introPrice, formattedIntroPrice) = firstPhase?.let { phase ->
                        val isTrial = phase.priceAmountMicros == 0L
                        if (isTrial) {
                            (phase.formattedPrice ?: "0") to 0.0
                        } else {
                            (phase.formattedPrice ?: "") to (phase.priceAmountMicros / 1_000_000.0)
                        }
                    } ?: ("" to 0.0)
                    
                    RenderItemProductDetails(
                        currencyCode = currencyCode,
                        currencySymbol = currencySymbol,
                        formattedPrice = formattedPrice,
                        price = price,
                        introPrice = introPrice,
                        formattedIntroPrice = formattedIntroPrice
                    )
                } else {
                    RenderItemProductDetails.empty()
                }
            } ?: RenderItemProductDetails.empty()

            val renderItem = RenderItem(
                itemId = product.productId,
                productDetails = productDetailsData
            )

            items.add(renderItem)
        }
        return items
    }

    /**
     * Получает символ валюты по коду валюты
     */
    private fun getCurrencySymbol(currencyCode: String): String? {
        return try {
            val currency = Currency.getInstance(currencyCode)
            currency.symbol
        } catch (e: Exception) {
            ApphudLog.logE("Error getting currency symbol for $currencyCode: ${e.message}")
            null
        }
    }
}
