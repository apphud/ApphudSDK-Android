package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.domain.Attribution

class AttributionMapper {
    fun map(dto: AttributionDto) = com.apphud.sdk.domain.Attribution(success = dto.success)
}
