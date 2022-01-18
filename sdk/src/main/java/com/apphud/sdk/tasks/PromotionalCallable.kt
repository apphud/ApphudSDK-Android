package com.apphud.sdk.tasks

import com.apphud.sdk.body.GrantPromotionalBody
import com.apphud.sdk.client.ApphudServiceV1
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto

internal class PromotionalCallable (
    private val body: GrantPromotionalBody,
    private val serviceV1: ApphudServiceV1
) : PriorityCallable<ResponseDto<CustomerDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<CustomerDto> = serviceV1.sendPromotionalRequest(body)

    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}