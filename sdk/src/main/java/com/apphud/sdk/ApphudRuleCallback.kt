package com.apphud.sdk

import android.app.Activity
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.internal.domain.model.Rule

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
}