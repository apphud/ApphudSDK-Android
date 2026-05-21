package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.internal.data.dto.CustomerDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudUser

internal class CustomerMapper(
    private val mapper: SubscriptionMapper,
    private var placementsMapper: PlacementsMapper,
) {
    /**
     * @param previousUser used to preserve fields when the server omits `scheme` entirely.
     * If `scheme` is present, all scheme-based fields are fully replaced with its payload
     * (including nulls). `scheme` is only returned by the backend when
     * `need_placements=true` was sent.
     */
    fun map(customer: CustomerDto, previousUser: ApphudUser? = null): ApphudUser {
        val scheme = customer.scheme

        val experimentName = if (scheme != null) scheme.experiment?.name else previousUser?.experimentName
        val variationName = if (scheme != null) scheme.variationName else previousUser?.variationName
        val targetingName = if (scheme != null) scheme.name else previousUser?.targetingName
        val remoteConfigString = if (scheme != null) scheme.remoteConfig else previousUser?.remoteConfigString

        return ApphudUser(
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
            placements =
            customer.placements?.let { placementsList ->
                placementsList.map { placementsMapper.map(it) }
            } ?: run {
                listOf()
            },
            isTemporary = false,
            totalDevicesCount = customer.totalDevicesCount ?: 0,
            internalId = customer.internalId.orEmpty(),
            experimentName = experimentName,
            variationName = variationName,
            targetingName = targetingName,
            remoteConfigString = remoteConfigString,
        )
    }
}
