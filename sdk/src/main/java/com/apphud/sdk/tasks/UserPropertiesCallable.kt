package com.apphud.sdk.tasks

import com.apphud.sdk.body.UserPropertiesBody
import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.ResponseDto

internal class UserPropertiesCallable(
    private val body: UserPropertiesBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<AttributionDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<AttributionDto> = service.sendUserProperties(body)
    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}