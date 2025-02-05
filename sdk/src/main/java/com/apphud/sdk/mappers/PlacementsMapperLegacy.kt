package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ApphudPlacementDto
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.parser.Parser

@Deprecated("Use PlacementsMapper")
internal class PlacementsMapperLegacy(
    parser: Parser,
) {
    private val paywallsMapperLegacy = PaywallsMapperLegacy(parser)

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
