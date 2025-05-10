package com.apphud.sdk.internal.data.mapper

import android.util.Base64
import com.apphud.sdk.internal.data.dto.RuleScreenDto
import com.apphud.sdk.internal.domain.model.RuleScreen
import java.nio.charset.StandardCharsets

internal class RuleScreenMapper {

    fun toDto(ruleScreen: RuleScreen): RuleScreenDto {
        val encodedHtml = Base64.encodeToString(
            ruleScreen.htmlScreen.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return RuleScreenDto(
            rule = ruleScreen.rule,
            encodedHtmlScreen = encodedHtml
        )
    }

    fun toDomain(ruleScreenDto: RuleScreenDto): RuleScreen {
        val decodedHtml = String(
            Base64.decode(ruleScreenDto.encodedHtmlScreen, Base64.NO_WRAP),
            StandardCharsets.UTF_8
        )
        return RuleScreen(
            rule = ruleScreenDto.rule,
            htmlScreen = decodedHtml
        )
    }
}
