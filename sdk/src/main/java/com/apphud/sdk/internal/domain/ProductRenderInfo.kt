package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.RenderResult
import com.apphud.sdk.internal.domain.model.RenderItemProductInfo
import java.util.Currency

internal fun ApphudPaywall.buildLocalRenderResult(): RenderResult =
    products?.map { it.buildMergedRenderInfo() } ?: emptyList()

internal fun ApphudProduct.buildMergedRenderInfo(): Map<String, Any> {
    val merged = mutableMapOf<String, Any>()
    merged.putAll(buildProductDetailsData().toRenderMap())
    jsonProperties()?.forEach { (key, value) -> merged[key] = value }
    itemId?.takeIf { it.isNotEmpty() }?.let { merged["item_id"] = it }
    return merged
}

internal fun ApphudProduct.buildProductDetailsData(): RenderItemProductInfo {
    val details = productDetails ?: return RenderItemProductInfo.empty()
    val subscriptionOffers = details.subscriptionOfferDetails

    if (subscriptionOffers.isNullOrEmpty()) {
        val oneTimeOffer = details.oneTimePurchaseOfferDetails ?: return RenderItemProductInfo.empty()
        val currencyCode = oneTimeOffer.priceCurrencyCode ?: ""
        return RenderItemProductInfo(
            currencyCode = currencyCode,
            currencySymbol = getCurrencySymbol(currencyCode) ?: "",
            formattedPrice = oneTimeOffer.formattedPrice ?: "",
            price = oneTimeOffer.priceAmountMicros / 1_000_000.0,
            introPrice = "",
            formattedIntroPrice = 0.0,
        )
    }

    val currencyCode = subscriptionOffers.firstOrNull()
        ?.pricingPhases
        ?.pricingPhaseList
        ?.firstOrNull()
        ?.priceCurrencyCode
        ?: ""
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

    return RenderItemProductInfo(
        currencyCode = currencyCode,
        currencySymbol = currencySymbol,
        formattedPrice = formattedPrice,
        price = price,
        introPrice = introPrice,
        formattedIntroPrice = formattedIntroPrice,
    )
}

internal fun RenderItemProductInfo.toRenderMap(): Map<String, Any> =
    mapOf(
        "currency_code" to currencyCode,
        "currency_symbol" to currencySymbol,
        "formatted_price" to formattedPrice,
        "price" to price,
        "intro_price" to introPrice,
        "formatted_intro_price" to formattedIntroPrice,
    )

internal fun resolveRenderItemId(item: Map<String, Any>): String? =
    item["item_id"]?.toString() ?: item["paywall_item_id"]?.toString()

/**
 * Merges locally built product info with backend-rendered properties.
 *
 * Merge order matches iOS `merge(_:uniquingKeysWith: { _, new in new })`:
 * local (store + dashboard) first, backend values win on duplicate keys.
 * Products without a backend entry (no Liquid macros) keep local data only.
 */
internal fun mergeRenderResults(
    local: RenderResult,
    backend: RenderResult,
): RenderResult {
    val backendByItemId = backend.mapNotNull { item ->
        resolveRenderItemId(item)?.let { id -> id to item }
    }.toMap()

    return local.map { localItem ->
        val itemId = resolveRenderItemId(localItem)
        val backendItem = itemId?.let { backendByItemId[it] }
        if (backendItem != null) {
            mergeRenderItem(localItem, backendItem)
        } else {
            localItem
        }
    }
}

internal fun mergeRenderItem(
    local: Map<String, Any>,
    backend: Map<String, Any>,
): Map<String, Any> {
    val merged = linkedMapOf<String, Any>()
    merged.putAll(local)
    flattenRenderItem(backend).forEach { (key, value) ->
        if (key != "item_id" && key != "paywall_item_id") {
            merged[key] = value
        }
    }
    return merged
}

internal fun flattenRenderItem(item: Map<String, Any>): Map<String, Any> {
    val flat = linkedMapOf<String, Any>()
    item.forEach { (key, value) ->
        when (key) {
            "product_info", "properties" -> {
                @Suppress("UNCHECKED_CAST")
                (value as? Map<String, Any>)?.forEach { (nestedKey, nestedValue) ->
                    flat[nestedKey] = nestedValue
                }
            }
            else -> flat[key] = value
        }
    }
    return flat
}

private fun getCurrencySymbol(currencyCode: String): String? {
    return try {
        Currency.getInstance(currencyCode).symbol
    } catch (e: Exception) {
        ApphudLog.logE("Error getting currency symbol for $currencyCode: ${e.message}")
        null
    }
}
