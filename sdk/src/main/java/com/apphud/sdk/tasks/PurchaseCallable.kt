package com.apphud.sdk.tasks

import com.apphud.sdk.body.PurchaseBody
import com.apphud.sdk.client.ApphudService
import com.apphud.sdk.client.dto.PurchaseResponseDto
import com.apphud.sdk.client.dto.ResponseDto

class PurchaseCallable(
    private val body: PurchaseBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<PurchaseResponseDto>> {
    override val incrementMilliseconds: Long = 5000
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<PurchaseResponseDto> = service.purchase(body)
}