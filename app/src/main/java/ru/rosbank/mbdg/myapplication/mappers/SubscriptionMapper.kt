package ru.rosbank.mbdg.myapplication.mappers

import ru.rosbank.mbdg.myapplication.client.dto.SubscriptionDto
import ru.rosbank.mbdg.myapplication.domain.Kind
import ru.rosbank.mbdg.myapplication.domain.Subscription

class SubscriptionMapper {

    fun map(dto: SubscriptionDto): Subscription =
        Subscription(
            status = dto.status,
            productId = dto.product_id,
            kind = Kind.valueOf(dto.kind),
            expiresAt = dto.expires_at,
            startedAt = dto.started_at,
            cancelledAt = dto.cancelled_at,
            inRetryBilling = dto.in_retry_billing,
            introductoryActivated = dto.introductory_activated,
            autoRenewEnabled = dto.autorenew_enabled
        )
}