package com.apphud.sdk.tasks

import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.dto.ProductDto
import com.apphud.sdk.client.dto.ResponseDto

class ProductsCallable(
    private val service: com.apphud.sdk.client.ApphudService
) : PriorityCallable<ResponseDto<List<ProductDto>>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<List<ProductDto>> = service.products()
}