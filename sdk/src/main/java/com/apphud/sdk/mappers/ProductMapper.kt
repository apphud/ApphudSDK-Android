package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ProductDto
import com.apphud.sdk.domain.Product

class ProductMapper {

    fun map(dto: ProductDto) : Product =
        Product(
            id = dto.id,
            productId = dto.product_id
        )
}