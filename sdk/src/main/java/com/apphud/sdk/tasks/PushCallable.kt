package com.apphud.sdk.tasks

import com.apphud.sdk.body.PushBody
import com.apphud.sdk.client.ApphudServiceV1
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.ResponseDto

internal class PushCallable(
    private val body: PushBody,
    private val serviceV1: ApphudServiceV1
) : PriorityCallable<ResponseDto<AttributionDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<AttributionDto> = serviceV1.send(body)
    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}