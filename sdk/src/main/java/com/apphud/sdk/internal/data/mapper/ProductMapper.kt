package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.internal.data.dto.ApphudGroupDto
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudProduct

internal class ProductMapper {
    fun map(dto: List<ApphudGroupDto>): List<ApphudGroup> =
        dto.map {
            ApphudGroup(
                id = it.id,
                name = it.name,
                products =
                    it.bundles.map { item ->
                        ApphudProduct(
                            id = item.id,
                            productId = item.productId,
                            name = item.name,
                            store = item.store,
                            basePlanId = item.basePlanId,
                            productDetails = null,
                            paywallId = null,
                            paywallIdentifier = null,
                            placementId = null,
                            placementIdentifier = null,
                        )
                    },
            )
        }
}
