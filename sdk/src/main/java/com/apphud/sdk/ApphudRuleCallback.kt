package com.apphud.sdk

import com.apphud.sdk.internal.domain.model.Rule

interface ApphudRuleCallback {
    /**
     * Called when a rule should be executed.
     * 
     * @param rule Apphud rule that should be executed
     * @return true if the rule should be executed; false if the rule should be ignored
     */
    fun shouldPerformRule(rule: Rule): Boolean = true

    /**
     * Called when a screen should be displayed.
     * If returns false, the screen will be stored in memory and can be
     * shown later using Apphud.showPendingScreen().
     * 
     * @param rule Apphud rule containing the screen that should be shown
     * @return true if the screen should be shown immediately; false if showing should be postponed
     */
    fun shouldShowScreen(rule: Rule): Boolean = true

    /**
     * Called when a purchase is completed from a rule screen.
     * 
     * @param rule Apphud rule from which the purchase was initiated
     * @param result ApphudPurchaseResult containing purchase details and error if any
     */
    fun onPurchaseCompleted(rule: Rule, result: ApphudPurchaseResult) = Unit
}