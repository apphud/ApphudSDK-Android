package ru.rosbank.mbdg.myapplication.presentation

import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.client.dto.ProductDto

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