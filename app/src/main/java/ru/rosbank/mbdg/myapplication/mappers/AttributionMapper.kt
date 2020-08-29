package ru.rosbank.mbdg.myapplication.mappers

import ru.rosbank.mbdg.myapplication.client.dto.AttributionDto
import ru.rosbank.mbdg.myapplication.domain.Attribution

class AttributionMapper {

    fun map(dto: AttributionDto) =
        Attribution(success = dto.success)
}