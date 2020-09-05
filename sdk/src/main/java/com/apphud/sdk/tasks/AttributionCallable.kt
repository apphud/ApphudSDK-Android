package com.apphud.sdk.tasks

import com.apphud.sdk.body.AttributionBody
import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.client.dto.ResponseDto

internal class AttributionCallable(
    private val body: AttributionBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<AttributionDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<AttributionDto> = service.send(body)
}