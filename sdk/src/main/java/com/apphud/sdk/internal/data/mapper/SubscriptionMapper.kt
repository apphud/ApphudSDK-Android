package com.apphud.sdk.internal.data.mapper

import com.apphud.sdk.DateTimeFormatter
import com.apphud.sdk.client.dto.SubscriptionDto
import com.apphud.sdk.domain.ApphudKind
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudSubscriptionStatus
import java.text.ParseException
import java.util.Date

internal class SubscriptionMapper {
    private fun buildDate(date: String?): Long? =
        try {
            date?.let { DateTimeFormatter.formatter.parse(it)?.time }
        } catch (e: ParseException) {
            null
        }

    fun mapRenewable(dto: SubscriptionDto): ApphudSubscription? =
        when (val expires = buildDate(dto.expiresAt)) {
            null -> null
            else ->
                ApphudSubscription(
                    status = ApphudSubscriptionStatus.map(dto.status),
                    productId = dto.productId,
                    kind = ApphudKind.map(dto.kind),
                    expiresAt = expires,
                    startedAt = buildDate(dto.startedAt) ?: Date().time,
                    cancelledAt = buildDate(dto.cancelledAt),
                    purchaseToken = dto.originalTransactionId,
                    isInRetryBilling = dto.inRetryBilling,
                    isIntroductoryActivated = dto.introductoryActivated,
                    isAutoRenewEnabled = dto.autorenewEnabled,
                    groupId = "",
                )
        }

    fun mapNonRenewable(dto: SubscriptionDto): ApphudNonRenewingPurchase? {
        return when (val purchase = buildDate(dto.startedAt)) {
            null -> null
            else ->
                ApphudNonRenewingPurchase(
                    productId = dto.productId,
                    purchasedAt = purchase,
                    canceledAt = buildDate(dto.cancelledAt),
                    purchaseToken = dto.originalTransactionId,
                    isConsumable = dto.isConsumable ?: false,
                )
        }
    }
}
