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
                rule = notificationDto.rule?.let { ruleDto ->
                    Rule(
                        id = ruleDto.id,
                        screenId = ruleDto.screenId,
                        ruleName = ruleDto.ruleName,
                        screenName = ruleDto.screenName
                    )
                },
                properties = notificationDto.properties
            )
        }
}