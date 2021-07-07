package com.apphud.sdk.tasks

import com.apphud.sdk.body.PaywallEventBody
import com.apphud.sdk.client.ApphudServiceV1
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.ResponseDto

internal class PaywallEventCallable(
    private val body: PaywallEventBody,
    private val service: ApphudServiceV1
) : PriorityCallable<ResponseDto<AttributionDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<AttributionDto> = service.sendPaywallEvent(body)

    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}