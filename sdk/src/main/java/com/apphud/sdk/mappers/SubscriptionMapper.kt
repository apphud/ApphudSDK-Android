package com.apphud.sdk.mappers

import com.apphud.sdk.client.dto.SubscriptionDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudSubscriptionStatus

class SubscriptionMapper {

    fun mapRenewable(dto: SubscriptionDto): ApphudSubscription =
        ApphudSubscription(
            status = ApphudSubscriptionStatus.map(dto.status),
            productId = dto.product_id,
            kind = ApphudKind.map(dto.kind),
            expiresAt = dto.expires_at,
            startedAt = dto.started_at,
            cancelledAt = dto.cancelled_at,
            isInRetryBilling = dto.in_retry_billing,
            isIntroductoryActivated = dto.introductory_activated,
            isAutoRenewEnabled = dto.autorenew_enabled
        )

    fun mapNonRenewable(dto: SubscriptionDto): ApphudNonRenewingPurchase =
        ApphudNonRenewingPurchase(
            productId = dto.product_id,
            purchasedAt = dto.started_at,
            canceledAt = dto.cancelled_at
        )
}