package com.apphud.sdk.internal.domain

import com.apphud.sdk.internal.data.local.LocalRulesScreenRepository
import kotlin.coroutines.cancellation.CancellationException

internal sealed class RuleScreenResult {
    data class Success(val ruleId: String) : RuleScreenResult()
    object NoRules : RuleScreenResult()
    data class Error(val message: String) : RuleScreenResult()
}

internal class FetchMostActualRuleScreenUseCase(
    private val localRulesScreenRepository: LocalRulesScreenRepository,
) {

    suspend operator fun invoke(): RuleScreenResult {
        return try {
            val allRuleScreens = localRulesScreenRepository.getAll().getOrThrow()

            if (allRuleScreens.isEmpty()) {
                RuleScreenResult.NoRules
            } else {
                val mostActualRuleScreen = allRuleScreens.minBy { it.createdAt }
                RuleScreenResult.Success(mostActualRuleScreen.rule.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RuleScreenResult.Error(e.message ?: "Unknown error")
        }
    }
}
