package ru.rosbank.mbdg.myapplication.mappers

import ru.rosbank.mbdg.myapplication.client.dto.SubscriptionDto
import ru.rosbank.mbdg.myapplication.domain.ApphudKind
import ru.rosbank.mbdg.myapplication.domain.ApphudSubscription

class SubscriptionMapper {

    fun map(dto: SubscriptionDto): ApphudSubscription =
        ApphudSubscription(
            status = dto.status,
            productId = dto.product_id,
            kind = ApphudKind.valueOf(dto.kind),
            expiresAt = dto.expires_at,
            startedAt = dto.started_at,
            cancelledAt = dto.cancelled_at,
            isInRetryBilling = dto.in_retry_billing,
            isIntroductoryActivated = dto.introductory_activated,
            isAutoRenewEnabled = dto.autorenew_enabled
        )
}