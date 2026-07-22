package com.apphud.sdk.internal.domain.mapper

import com.apphud.sdk.internal.data.dto.NotificationDto
import com.apphud.sdk.internal.domain.model.Notification
import com.apphud.sdk.domain.Rule

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
     * The backend delivers rule metadata (`rule_name`, `screen_name`, `paywall_identifier`,
     * `paywall_id`) inside `properties`, while the `rule` object carries its id and, when present,
     * the authoritative `screen_id` for that rule.
     *
     * The `properties.screen_id` and `rule.screen_id` may differ; the `rule` object is the source
     * of truth, so its `screen_id` takes precedence when available, falling back to
     * `properties.screen_id` otherwise.
     */
    private fun mapRule(notificationDto: NotificationDto): Rule? {
        val ruleDto = notificationDto.rule
        val properties = notificationDto.properties
        val id = ruleDto?.id ?: (properties?.get("rule_id") as? String) ?: return null
        return Rule(
            id = id,
            screenId = ruleDto?.screenId ?: (properties?.get("screen_id") as? String) ?: "",
            ruleName = (properties?.get("rule_name") as? String) ?: ruleDto?.ruleName,
            screenName = (properties?.get("screen_name") as? String) ?: ruleDto?.screenName,
            paywallIdentifier = properties?.get("paywall_identifier") as? String,
            paywallId = properties?.get("paywall_id") as? String,
        )
    }

    /**
     * Builds a [Rule] from a push notification data payload. The payload always contains
     * `rule_id`, `screen_id` and `screen_name`; new (Figma) rules additionally carry
     * `paywall_id` (and usually `paywall_identifier`).
     *
     * Mirrors iOS which merges `["id": rule_id]` with the notification payload.
     */
    fun mapRuleFromPayload(payload: Map<String, Any>): Rule? {
        val id = payload["rule_id"] as? String ?: return null
        return Rule(
            id = id,
            screenId = (payload["screen_id"] as? String) ?: "",
            ruleName = payload["rule_name"] as? String,
            screenName = payload["screen_name"] as? String,
            paywallIdentifier = payload["paywall_identifier"] as? String,
            paywallId = payload["paywall_id"] as? String,
        )
    }
}