package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.Customer
import com.apphud.sdk.domain.ApphudUser

class CustomerMapper(
    private val mapper: SubscriptionMapper,
    private val paywallsMapper: PaywallsMapper
) {

    fun map(customer: CustomerDto) = Customer(
        user = ApphudUser(
            userId = customer.user_id,
            currencyCode = customer.currency?.code,
            currencyCountryCode = customer.currency?.country_code
        ),
        subscriptions = customer.subscriptions
            .filter { it.kind == ApphudKind.AUTORENEWABLE.source }
            .mapNotNull { mapper.mapRenewable(it) }
            .sortedByDescending { it.expiresAt },
        purchases = customer.subscriptions
            .filter { it.kind == ApphudKind.NONRENEWABLE.source }
            .mapNotNull { mapper.mapNonRenewable(it) }
            .sortedByDescending { it.purchasedAt },
        paywalls = customer.paywalls.mapNotNull{paywallsMapper.map(it)}
    )
}