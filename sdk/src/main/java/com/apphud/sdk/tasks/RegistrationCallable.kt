package com.apphud.sdk.tasks

import com.apphud.sdk.body.RegistrationBody
import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.client.dto.ResponseDto

internal class RegistrationCallable(
    private val body: RegistrationBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<CustomerDto>> {
    override val priority: Int = Int.MIN_VALUE
    override fun call(): ResponseDto<CustomerDto> = service.registration(body)
}


