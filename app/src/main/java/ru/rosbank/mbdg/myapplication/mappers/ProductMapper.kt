package ru.rosbank.mbdg.myapplication.mappers

import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.domain.ProductModel

class ProductMapper {

    fun map(dto: ProductDto) =
        ProductModel(
            productId = dto.product_id,
            details = null
        )

    fun map(details: SkuDetails) =
        ProductModel(
            productId = details.sku,
            details = details
        )
}