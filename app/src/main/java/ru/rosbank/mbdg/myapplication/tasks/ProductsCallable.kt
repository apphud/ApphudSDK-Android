package ru.rosbank.mbdg.myapplication.tasks

import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.dto.ProductDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto

class ProductsCallable(
    private val service: ApphudService
) : PriorityCallable<ResponseDto<List<ProductDto>>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<List<ProductDto>> = service.products()
}