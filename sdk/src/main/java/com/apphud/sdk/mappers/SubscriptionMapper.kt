package com.apphud.sdk.mappers

import com.apphud.sdk.DateTimeFormatter
import com.apphud.sdk.client.dto.SubscriptionDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudSubscriptionStatus
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class SubscriptionMapper {

    private val parser = SimpleDateFormat(DateTimeFormatter.pattern, Locale.getDefault())

    private fun buildDate(date: String?): Long? = try {
        date?.let { parser.parse(it)?.time }
    } catch (e: ParseException) {
        null
    }

    fun mapRenewable(dto: SubscriptionDto): ApphudSubscription? =
        when (val expires = buildDate(dto.expires_at)) {
            null -> null
            else -> ApphudSubscription(
                status = ApphudSubscriptionStatus.map(dto.status),
                productId = dto.product_id,
                kind = ApphudKind.map(dto.kind),
                expiresAt = expires,
                startedAt = buildDate(dto.started_at),
                cancelledAt = buildDate(dto.cancelled_at),
                isInRetryBilling = dto.in_retry_billing,
                isIntroductoryActivated = dto.introductory_activated,
                isAutoRenewEnabled = dto.autorenew_enabled
            )
        }

    fun mapNonRenewable(dto: SubscriptionDto): ApphudNonRenewingPurchase? {
        return when (val purchase = buildDate(dto.started_at)) {
            null -> null
            else -> ApphudNonRenewingPurchase(
                productId = dto.product_id,
                purchasedAt = purchase,
                canceledAt = buildDate(dto.cancelled_at)
            )
        }
    }
}