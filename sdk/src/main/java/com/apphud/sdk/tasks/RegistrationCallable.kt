package com.apphud.sdk.tasks

import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.client.ApphudServiceV1
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto

internal class RegistrationCallable(
    private val body: RegistrationBody,
    private val serviceV1: ApphudServiceV1
) : PriorityCallable<ResponseDto<CustomerDto>> {
    override val priority: Int = Int.MIN_VALUE
    override fun call(): ResponseDto<CustomerDto> = serviceV1.registration(body)

    private var _counter: Int = 0
    override var counter: Int
        get() = _counter
        set(value) {
            _counter = value
        }
}


