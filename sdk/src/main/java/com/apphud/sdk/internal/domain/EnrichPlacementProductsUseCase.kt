package com.apphud.sdk.internal.domain

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
        val productDetailsById = productRepository.state.value.products.associateBy { it.productId }

        user.placements.forEach { placement ->
            val paywall = placement.paywall ?: return@forEach
            paywall.placementId = placement.id
            paywall.placementIdentifier = placement.identifier
            paywall.products?.forEach { product ->
                product.paywallId = paywall.id
                product.paywallIdentifier = paywall.identifier
                product.placementId = placement.id
                product.placementIdentifier = placement.identifier
                product.productDetails = productDetailsById[product.productId]
            }
        }
    }
}
