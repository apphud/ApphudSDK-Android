package com.apphud.sdk.internal.domain

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.RenderResult
import com.apphud.sdk.internal.data.remote.RenderRemoteRepository
import com.apphud.sdk.internal.domain.model.RenderItem

/**
 * Use case responsible for rendering paywall product properties.
 *
 * This class handles the process of checking if paywall properties need to be rendered
 * and sending them to the backend to replace macros.
 */
internal class RenderPaywallPropertiesUseCase(
    private val renderRemoteRepository: RenderRemoteRepository,
) {

    /**
     * Renders paywall properties if needed.
     *
     * Store [product_info] is always merged with dashboard [properties] locally (iOS parity).
     * Backend rendering runs only when Liquid macros are present.
     *
     * @param paywall The paywall whose properties need to be rendered
     */
    suspend operator fun invoke(paywall: ApphudPaywall): Result<RenderResult> {
        val localResult = paywall.buildLocalRenderResult()
        val items = itemsToRender(paywall)

        if (items.isEmpty()) {
            ApphudLog.log("No products macros to render, skipping")
            return Result.success(localResult)
        }

        ApphudLog.log("renderPropertiesIfNeeded: sending ${items.size} items to backend")

        return renderRemoteRepository.renderPaywallProperties(items)
            .map { backendResult -> mergeRenderResults(localResult, backendResult) }
    }

    /**
     * Returns list of items that should be rendered on backend to replace Liquid macros.
     * Simple `{macro}` placeholders are resolved locally and do not require a backend call.
     */
    private fun itemsToRender(paywall: ApphudPaywall): List<RenderItem> {
        val items = mutableListOf<RenderItem>()
        paywall.products?.forEach { product ->
            if (!product.hasLiquidMacros()) {
                return@forEach
            }

            items.add(
                RenderItem(
                    itemId = product.itemId ?: "",
                    productDetails = product.buildProductDetailsData(),
                )
            )
        }
        return items
    }
}
