package ru.rosbank.mbdg.myapplication.presentation

import com.android.billingclient.api.SkuDetails
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto

class ProductModelMapper {

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