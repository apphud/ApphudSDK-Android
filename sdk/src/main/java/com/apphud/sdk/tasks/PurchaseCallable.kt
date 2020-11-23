package com.apphud.sdk.tasks

import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto

internal class PurchaseCallable(
    private val body: PurchaseBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<CustomerDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<CustomerDto> = service.purchase(body)

    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}