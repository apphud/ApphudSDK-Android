package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.ApphudPaywallDto
import com.apphud.sdk.client.dto.ApphudPlacementDto
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.parser.Parser

class PlacementsMapper(
    private val parser: Parser
){

    fun map(dto: List<ApphudPlacementDto>): List<ApphudPlacement> = dto.map { placementDto -> map (placementDto) }

    fun map(placementDto: ApphudPlacementDto) = ApphudPlacement(
        id = placementDto.id,
        identifier = placementDto.identifier,
        paywall = placementDto.paywalls.firstOrNull(),
        experimentName = placementDto.experiment_name
    )
}