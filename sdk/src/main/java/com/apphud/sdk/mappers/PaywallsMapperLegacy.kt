package com.apphud.sdk.mappers

import com.apphud.sdk.internal.data.dto.ApphudPaywallDto
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudPaywallScreen
import com.apphud.sdk.parser.Parser

@Deprecated("Use PaywallsMapper")
internal class PaywallsMapperLegacy(
    private val parser: Parser,
) {
    fun map(dto: List<ApphudPaywallDto>): List<ApphudPaywall> = dto.map { paywallDto -> map(paywallDto) }

    fun map(paywallDto: ApphudPaywallDto) =
        ApphudPaywall(
            id = paywallDto.id, // paywall id
            name = paywallDto.name,
            identifier = paywallDto.identifier,
            default = paywallDto.default,
            json = parser.fromJson<Map<String, Any>>(paywallDto.json, Map::class.java),
            products =
            paywallDto.items.map { item ->
                ApphudProduct(
                    id = item.id, // product bundle id
                    productId = item.productId,
                    name = item.name,
                    store = item.store,
                    basePlanId = item.basePlanId,
                    productDetails = null,
                    paywallId = paywallDto.id,
                    paywallIdentifier = paywallDto.identifier,
                    placementId = null,
                    placementIdentifier = null,
                )
            },
            screen = paywallDto.screen?.let { ApphudPaywallScreen(id = it.id, defaultUrl = it.defaultURL, urls = it.urls) },
            experimentName = paywallDto.experimentName,
            placementId = null,
            placementIdentifier = null,
            parentPaywallIdentifier = paywallDto.fromPaywall,
            variationName = paywallDto.variationName
        )
}
