package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ProductDto
import com.apphud.sdk.domain.Product

class ProductMapper {

    fun map(dto: ProductDto) : com.apphud.sdk.domain.Product =
        com.apphud.sdk.domain.Product(
            id = dto.id,
            dbId = dto.db_id,
            groupId = dto.group_id,
            productId = dto.product_id
        )
}