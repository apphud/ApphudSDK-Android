package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.parser.Parser

class PaywallsMapper {

    fun map(dto: List<ApphudPaywallDto>, parser: Parser): List<ApphudPaywall> =
        dto.map { paywallDto ->
            ApphudPaywall(
                id = paywallDto.id, //paywall id
                name = paywallDto.name,
                identifier = paywallDto.identifier,
                default = paywallDto.default,
                json = parser.fromJson<Map<String, Any>>(paywallDto.json, Map::class.java),
                products = paywallDto.items.map { item ->
                    ApphudProduct(
                        id = item.id,//product id
                        product_id = item.product_id,
                        name = item.name,
                        store = item.store,
                        skuDetails = null,
                        paywall_id = paywallDto.id //paywall id
                    )
                }
            )
        }
}
