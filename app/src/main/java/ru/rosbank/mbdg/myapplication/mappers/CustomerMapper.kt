package ru.rosbank.mbdg.myapplication.mappers

import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.domain.ApphudKind
import ru.rosbank.mbdg.myapplication.domain.Customer
import ru.rosbank.mbdg.myapplication.domain.ApphudUser

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
            .filter { it.kind == ApphudKind.AUTORENEWABLE.name }
            .map { mapper.mapRenewable(it) }
            .sortedBy { it.expiresAt },
        purchases = customer.subscriptions
            .filter { it.kind == ApphudKind.NONRENEWABLE.name }
            .map { mapper.mapNonRenewable(it) }
            .sortedBy { it.purchasedAt }
    )
}