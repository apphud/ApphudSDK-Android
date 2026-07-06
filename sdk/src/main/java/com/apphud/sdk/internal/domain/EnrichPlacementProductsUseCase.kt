package com.apphud.sdk.internal.domain

import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.internal.data.ProductRepository
import com.apphud.sdk.internal.data.UserRepository

/**
 * Attaches loaded Google Play [ProductDetails] to [ApphudProduct] instances inside
 * the current user's placements and sets placement/paywall metadata on products.
 */
internal class EnrichPlacementProductsUseCase(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
) {
    operator fun invoke(user: ApphudUser? = userRepository.getCurrentUser()) {
        user ?: return
        val productDetailsById = productDetailsById()

        user.placements.forEach { placement ->
            val paywall = placement.paywall ?: return@forEach
            paywall.placementId = placement.id
            paywall.placementIdentifier = placement.identifier
            enrichPaywallProducts(
                paywall = paywall,
                productDetailsById = productDetailsById,
                placementId = placement.id,
                placementIdentifier = placement.identifier,
            )
        }
    }

    fun enrichPaywall(paywall: ApphudPaywall) {
        enrichPaywallProducts(
            paywall = paywall,
            productDetailsById = productDetailsById(),
            placementId = paywall.placementId,
            placementIdentifier = paywall.placementIdentifier,
        )
    }

    private fun productDetailsById() =
        productRepository.state.value.products.associateBy { it.productId }

    private fun enrichPaywallProducts(
        paywall: ApphudPaywall,
        productDetailsById: Map<String, ProductDetails>,
        placementId: String?,
        placementIdentifier: String?,
    ) {
        paywall.products?.forEach { product ->
            product.paywallId = paywall.id
            product.paywallIdentifier = paywall.identifier
            product.placementId = placementId
            product.placementIdentifier = placementIdentifier
            product.productDetails = productDetailsById[product.productId]
        }
    }
}
