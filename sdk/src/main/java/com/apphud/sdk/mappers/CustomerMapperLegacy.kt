package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudUser

@Deprecated("Use CustomerMapper")
internal class CustomerMapperLegacy(
    private val mapper: SubscriptionMapperLegacy,
    private val paywallsMapperLegacy: PaywallsMapperLegacy,
    private var placementsMapperLegacy: PlacementsMapperLegacy,
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
                    paywallsList.map { paywallsMapperLegacy.map(it) }
                } ?: run {
                    listOf()
                },
            placements =
                customer.placements?.let { placementsList ->
                    placementsList.map { placementsMapperLegacy.map(it) }
                } ?: run {
                    listOf()
                },
            isTemporary = false,
        )
}
