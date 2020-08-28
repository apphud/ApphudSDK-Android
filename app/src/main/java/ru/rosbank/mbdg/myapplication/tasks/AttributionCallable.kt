package ru.rosbank.mbdg.myapplication.tasks

import ru.rosbank.mbdg.myapplication.body.AttributionBody
import ru.rosbank.mbdg.myapplication.client.ApphudService
import ru.rosbank.mbdg.myapplication.client.dto.AttributionDto
import ru.rosbank.mbdg.myapplication.client.dto.ResponseDto

class AttributionCallable(
    private val body: AttributionBody,
    private val service: ApphudService
) : PriorityCallable<ResponseDto<AttributionDto>> {
    override val priority: Int = Int.MAX_VALUE
    override fun call(): ResponseDto<AttributionDto> = service.send(body)
}