package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.parser.Parser

class PaywallsMapper {

    fun map(dto: List<ApphudPaywallDto>, parser: Parser): List<ApphudPaywall> {
        return dto.map {
            ApphudPaywall(
                id = it.id,
                name = it.name,
                identifier = it.identifier,
                default = it.default,
                json = parser.fromJson<Map<String, Any>>(it.json, Map::class.java),
                products = it.items.map { item ->
                    ApphudProduct(
                        id = item.id,
                        productId = item.product_id,
                        name = item.name,
                        store = item.store,
                        skuDetails = null
                    )
                }
            )
        }
    }
}