package com.apphud.sdk.internal.domain.mapper

import com.apphud.sdk.internal.data.dto.NotificationDto
import com.apphud.sdk.internal.data.dto.RuleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationMapperTest {

    private val mapper = NotificationMapper()

    private fun notificationDto(
        rule: RuleDto? = ruleDto(),
        properties: Map<String, Any>? = null,
    ) = NotificationDto(
        id = "notification-id",
        createdAt = "2024-01-01T00:00:00Z",
        rule = rule,
        properties = properties,
    )

    private fun ruleDto() = RuleDto(
        id = "rule-id",
        screenId = "screen-id",
        ruleName = "rule-name",
        screenName = "screen-name",
    )

    // region paywall_identifier

    @Test
    fun `GIVEN properties with paywall_identifier EXPECT rule paywallIdentifier is set`() {
        val dto = notificationDto(properties = mapOf("paywall_identifier" to "main_paywall"))

        val result = mapper.map(listOf(dto))

        assertEquals("main_paywall", result.single().rule?.paywallIdentifier)
    }

    @Test
    fun `GIVEN null properties EXPECT rule paywallIdentifier is null`() {
        val dto = notificationDto(properties = null)

        val result = mapper.map(listOf(dto))

        assertNull(result.single().rule?.paywallIdentifier)
    }

    @Test
    fun `GIVEN properties without paywall_identifier EXPECT rule paywallIdentifier is null`() {
        val dto = notificationDto(properties = mapOf("other_key" to "value"))

        val result = mapper.map(listOf(dto))

        assertNull(result.single().rule?.paywallIdentifier)
    }

    @Test
    fun `GIVEN non-string paywall_identifier EXPECT rule paywallIdentifier is null`() {
        val dto = notificationDto(properties = mapOf("paywall_identifier" to 123))

        val result = mapper.map(listOf(dto))

        assertNull(result.single().rule?.paywallIdentifier)
    }

    // endregion

    // region no rule

    @Test
    fun `GIVEN notification without rule EXPECT rule is null`() {
        val dto = notificationDto(rule = null)

        val result = mapper.map(listOf(dto))

        assertNull(result.single().rule)
    }

    // endregion
}
