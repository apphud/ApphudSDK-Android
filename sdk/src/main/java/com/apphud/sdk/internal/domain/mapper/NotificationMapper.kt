package com.apphud.sdk.internal.domain.mapper

import com.apphud.sdk.internal.data.dto.NotificationDto
import com.apphud.sdk.internal.domain.model.Notification
import com.apphud.sdk.internal.domain.model.Rule

internal class NotificationMapper {
    fun map(dto: List<NotificationDto>): List<Notification> =
        dto.map { notificationDto ->
            Notification(
                id = notificationDto.id,
                createdAt = notificationDto.createdAt,
                rule = mapRule(notificationDto),
                properties = notificationDto.properties,
            )
        }

    /**
     * The backend delivers rule metadata (`screen_id`, `rule_name`, `screen_name`,
     * `paywall_identifier`) inside `properties`, while the `rule` object only carries its id.
     * Mirror iOS, which merges `rule` + `properties`, preferring `properties` and falling back
     * to any values present on the `rule` object.
     */
    private fun mapRule(notificationDto: NotificationDto): Rule? {
        val ruleDto = notificationDto.rule ?: return null
        val properties = notificationDto.properties
        val id = ruleDto.id ?: (properties?.get("rule_id") as? String) ?: return null
        return Rule(
            id = id,
            screenId = (properties?.get("screen_id") as? String) ?: ruleDto.screenId ?: "",
            ruleName = (properties?.get("rule_name") as? String) ?: ruleDto.ruleName,
            screenName = (properties?.get("screen_name") as? String) ?: ruleDto.screenName,
            paywallIdentifier = properties?.get("paywall_identifier") as? String,
        )
    }
}