package com.apphud.sdk.tasks

import com.apphud.sdk.client.ApphudServiceV2
import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.client.dto.ResponseDto

internal class PaywallsCallable(
    private val service: ApphudServiceV2
) : PriorityCallable<ResponseDto<List<ApphudPaywallDto>>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<List<ApphudPaywallDto>> = service.getPaywalls()

    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}