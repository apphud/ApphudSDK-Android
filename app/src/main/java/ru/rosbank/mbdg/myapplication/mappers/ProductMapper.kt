package ru.rosbank.mbdg.myapplication.mappers

import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.domain.Product

class ProductMapper {

    fun map(dto: ProductDto) : Product =
        Product(
            id = dto.id,
            dbId = dto.db_id,
            groupId = dto.group_id,
            productId = dto.product_id
        )
}