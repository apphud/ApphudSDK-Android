package ru.rosbank.mbdg.myapplication.mappers

import ru.rosbank.mbdg.myapplication.client.dto.CustomerDto
import ru.rosbank.mbdg.myapplication.domain.Customer
import ru.rosbank.mbdg.myapplication.domain.ApphudUser

class CustomerMapper(
    private val mapper: SubscriptionMapper
) {

    fun map(customer: CustomerDto): Customer =
        Customer(
            user = ApphudUser(
                userId = customer.user_id,
                currencyCode = customer.currency?.code,
                currencyCountryCode = customer.currency?.country_code
            ),
            subscriptions = customer.subscriptions.map { mapper.map(it) }
        )
}