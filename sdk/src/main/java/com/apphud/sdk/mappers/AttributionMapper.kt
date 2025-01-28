package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.AttributionDto
import com.apphud.sdk.domain.Attribution

internal class AttributionMapper {
    fun map(dto: AttributionDto) = Attribution(success = dto.success)
}
