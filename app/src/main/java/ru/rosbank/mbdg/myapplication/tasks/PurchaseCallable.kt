package ru.rosbank.mbdg.myapplication.tasks

import ru.rosbank.mbdg.myapplication.body.PurchaseBody
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.dto.PurchaseResponseDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto

class PurchaseCallable(
    private val body: PurchaseBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<PurchaseResponseDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<PurchaseResponseDto> = service.purchase(body)
}