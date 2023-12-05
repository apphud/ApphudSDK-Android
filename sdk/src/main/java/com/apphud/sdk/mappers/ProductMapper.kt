package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ApphudGroupDto
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudProduct

class ProductMapper {
    fun map(dto: List<ApphudGroupDto>): List<ApphudGroup> =
        dto.map {
            ApphudGroup(
                id = it.id,
                name = it.name,
                products =
                    it.bundles.map { item ->
                        ApphudProduct(
                            id = item.id,
                            product_id = item.product_id,
                            name = item.name,
                            store = item.store,
                            basePlanId = item.base_plan_id,
                            productDetails = null,
                            paywall_id = null,
                            paywall_identifier = null,
                        )
                    },
            )
        }
}
