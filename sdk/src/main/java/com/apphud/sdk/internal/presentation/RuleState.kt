package com.apphud.sdk.internal.presentation

import com.apphud.sdk.internal.domain.model.Rule

internal sealed class RuleState {
    object Idle : RuleState()
    object Loading : RuleState()
    data class RuleActivityAlreadyOpen(val rule: Rule) : RuleState()
    data class RuleActivityClosed(val rule: Rule) : RuleState()
    data class PendingRule(val rule: Rule) : RuleState()
}
