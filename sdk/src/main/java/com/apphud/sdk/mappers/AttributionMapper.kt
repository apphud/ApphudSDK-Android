package com.apphud.sdk.mappers

import com.apphud.sdk.domain.Attribution
import com.apphud.sdk.internal.data.dto.AttributionDto

internal class AttributionMapper {
    fun map(dto: AttributionDto) = Attribution(success = dto.success)
}
