package com.apphud.sdk

import android.app.Activity
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.Rule

/**
 * Action performed after a survey option is selected or feedback is sent on a legacy HTML
 * rule screen.
 *
 * Mirrors iOS `ApphudScreenDismissAction`.
 */
enum class ApphudScreenDismissAction {
    /**
     * Displays a "Thank you for feedback" ("Answer sent" / "Feedback sent") dialog and then
     * dismisses the screen. This is the default behavior.
     */
    THANK_AND_CLOSE,

    /**
     * Dismisses the screen without showing any dialog.
     */
    CLOSE_ONLY,

    /**
     * Does nothing. The screen stays open so you can handle presentation yourself.
     */
    NONE,
}

interface ApphudRuleCallback {
    /**
     * Called when the SDK is about to present a rule-triggered paywall screen and needs an
     * Activity to display it on. Return the currently visible Activity to present the screen
     * on top of it. If null is returned, the SDK falls back to launching the screen in a new task
     * using the application context.
     *
     * Mirrors iOS `ApphudUIDelegate.apphudParentViewController(controller:)`.
     *
     * @return the Activity to present the paywall screen on, or null to use the application context
     */
    fun provideActivity(): Activity? = null

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

    /**
     * Called when the user taps a purchase button on a rule-triggered paywall screen, right
     * before the purchase flow starts.
     *
     * Mirrors iOS `ApphudUIDelegate.apphudWillPurchase(product:offerID:screenName:)`.
     *
     * @param rule Apphud rule from which the purchase is initiated
     * @param product the product being purchased, or null for a restore action
     */
    fun onWillPurchase(rule: Rule, product: ApphudProduct?) = Unit

    /**
     * Called when a rule-triggered paywall screen is loaded and visible to the user.
     *
     * Mirrors iOS `ApphudUIDelegate.apphudScreenDidAppear(screenName:)`.
     *
     * @param rule Apphud rule whose screen appeared
     */
    fun onScreenAppeared(rule: Rule) = Unit

    /**
     * Called when a rule-triggered paywall screen is about to be dismissed.
     *
     * Mirrors iOS `ApphudUIDelegate.apphudScreenWillDismiss(screenName:error:)`.
     *
     * @param rule Apphud rule whose screen is dismissing
     * @param error error that caused the dismissal, or null if dismissed normally
     */
    fun onScreenWillDismiss(rule: Rule, error: ApphudError?) = Unit

    /**
     * Called after a rule-triggered paywall screen has been dismissed.
     *
     * Mirrors iOS `ApphudUIDelegate.apphudDidDismissScreen(controller:screenName:)`.
     *
     * @param rule Apphud rule whose screen was dismissed
     */
    fun onScreenDidDismiss(rule: Rule) = Unit

    /**
     * Called after a survey answer is selected on a legacy HTML rule screen.
     *
     * Mirrors iOS `ApphudUIDelegate.apphudDidSelectSurveyAnswer(question:answer:screenName:)`.
     *
     * @param rule Apphud rule whose screen the answer was selected on
     * @param question the survey question
     * @param answer the selected answer
     */
    fun onDidSelectSurveyAnswer(rule: Rule, question: String, answer: String) = Unit

    /**
     * Overrides the action performed after a survey option is selected or feedback is sent on a
     * legacy HTML rule screen. Default is [ApphudScreenDismissAction.THANK_AND_CLOSE].
     *
     * Mirrors iOS `ApphudUIDelegate.apphudScreenDismissAction(screenName:controller:)`.
     *
     * @param rule Apphud rule whose screen is being dismissed
     * @return the dismiss action to perform
     */
    fun onScreenDismissAction(rule: Rule): ApphudScreenDismissAction = ApphudScreenDismissAction.THANK_AND_CLOSE

    /**
     * Called for a Figma paywall rule whose resolved paywall has no screen payload. In this case
     * the SDK cannot present a screen by itself, so you should present the paywall using your own
     * UI.
     * 
     * Important: Purchases made using manual paywall handling will not be tracked in Rule analytics.
     *
     * @param rule Apphud rule that was triggered
     * @param paywall the resolved paywall associated with the rule
     */
    fun onRulePaywallWithoutScreen(rule: Rule, paywall: ApphudPaywall) = Unit
}