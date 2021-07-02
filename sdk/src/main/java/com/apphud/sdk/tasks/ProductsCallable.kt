package com.apphud.sdk.tasks

import com.apphud.sdk.client.ApphudServiceV2
import com.apphud.sdk.client.dto.ApphudGroupDto
import com.apphud.sdk.client.dto.ResponseDto

internal class ProductsCallable(
    private val service: ApphudServiceV2
) : PriorityCallable<ResponseDto<List<ApphudGroupDto>>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<List<ApphudGroupDto>> = service.products()

    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}