package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudUser

internal class CustomerMapper(
    private val mapper: SubscriptionMapper,
    private val paywallsMapper: PaywallsMapper,
    private var placementsMapper: PlacementsMapper,
) {
    fun map(customer: CustomerDto) =
        ApphudUser(
            userId = customer.userId,
            currencyCode = customer.currency?.code,
            countryCode = customer.currency?.countryCode,
            subscriptions =
            customer.subscriptions
                .filter { it.kind == ApphudKind.AUTORENEWABLE.source }
                .mapNotNull { mapper.mapRenewable(it) }
                .sortedByDescending { it.expiresAt },
            purchases =
            customer.subscriptions
                .filter { it.kind == ApphudKind.NONRENEWABLE.source }
                .mapNotNull { mapper.mapNonRenewable(it) }
                .sortedByDescending { it.purchasedAt },
            paywalls =
            customer.paywalls?.let { paywallsList ->
                paywallsList.map { paywallsMapper.map(it) }
            } ?: run {
                listOf()
            },
            placements =
            customer.placements?.let { placementsList ->
                placementsList.map { placementsMapper.map(it) }
            } ?: run {
                listOf()
            },
            isTemporary = false,
        )
}
