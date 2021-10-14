package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.parser.Parser

class PaywallsMapper(
    private val parser: Parser
){

    fun map(dto: List<ApphudPaywallDto>): List<ApphudPaywall> =
        dto.map { paywallDto -> map (paywallDto)
        }

    fun map(paywallDto: ApphudPaywallDto) = ApphudPaywall(
                id = paywallDto.id, //paywall id
                name = paywallDto.name,
                identifier = paywallDto.identifier,
                default = paywallDto.default,
                experimentId = paywallDto.experiment_id,
                variationIdentifier = paywallDto.variation_identifier,
                json = parser.fromJson<Map<String, Any>>(paywallDto.json, Map::class.java),
                products = paywallDto.items.map { item ->
                    ApphudProduct(
                        id = item.id,//product bundle id
                        product_id = item.product_id,
                        name = item.name,
                        store = item.store,
                        skuDetails = null,
                        paywall_id = paywallDto.id,
                        paywall_identifier = paywallDto.identifier
                    )
                },
                experimentName = paywallDto.experiment_name,
                variationName = paywallDto.variation_name
            )
}
