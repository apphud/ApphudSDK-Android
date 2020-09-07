package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.CustomerDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.Customer
import com.apphud.sdk.domain.ApphudUser

class CustomerMapper(
    private val mapper: SubscriptionMapper
) {

    fun map(customer: CustomerDto) = Customer(
        user = ApphudUser(
            userId = customer.user_id,
            currencyCode = customer.currency?.code,
            currencyCountryCode = customer.currency?.country_code
        ),
        subscriptions = customer.subscriptions
            .filter { it.kind == ApphudKind.AUTORENEWABLE.source }
            .map { mapper.mapRenewable(it) }
            .sortedBy { it.expiresAt },
        purchases = customer.subscriptions
            .filter { it.kind == ApphudKind.NONRENEWABLE.source }
            .map { mapper.mapNonRenewable(it) }
            .sortedBy { it.purchasedAt }
    )
}