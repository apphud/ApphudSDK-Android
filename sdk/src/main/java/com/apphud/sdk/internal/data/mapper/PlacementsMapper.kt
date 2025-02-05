package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.client.dto.ApphudPlacementDto
import com.apphud.sdk.domain.ApphudPlacement
import com.google.gson.Gson

internal class PlacementsMapper(
    private val paywallsMapperLegacy: PaywallsMapper,
) {

    fun map(dto: List<ApphudPlacementDto>): List<ApphudPlacement> = dto.map { placementDto -> map(placementDto) }

    fun map(placementDto: ApphudPlacementDto): ApphudPlacement {
        val paywallDto = placementDto.paywalls.firstOrNull()
        val paywallObject =
            if (paywallDto != null) {
                paywallsMapperLegacy.map(paywallDto)
            } else {
                null
            }

        return ApphudPlacement(
            id = placementDto.id,
            identifier = placementDto.identifier,
            paywall = paywallObject
        )
    }
}
