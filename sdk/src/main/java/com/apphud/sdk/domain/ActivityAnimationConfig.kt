package com.apphud.sdk.domain

import androidx.annotation.AnimRes

/**
 * Configuration object that defines transition animations for showing and dismissing
 * the paywall Activity. The SDK will apply them via overridePendingTransition.
 */
class ActivityAnimationConfig {

    @AnimRes
    private var showEnterAnimation: Int = android.R.anim.fade_in

    @AnimRes
    private var showExitAnimation: Int = android.R.anim.fade_out

    @AnimRes
    private var dismissEnterAnimation: Int = android.R.anim.fade_in

    @AnimRes
    private var dismissExitAnimation: Int = android.R.anim.fade_out

    /** Sets the animations for showing the paywall Activity. */
    fun setShowAnimation(@AnimRes enter: Int, @AnimRes exit: Int): ActivityAnimationConfig = apply {
        this.showEnterAnimation = enter
        this.showExitAnimation = exit
    }

    /** Sets the animations for dismissing the paywall Activity. */
    fun setDismissAnimation(@AnimRes enter: Int, @AnimRes exit: Int): ActivityAnimationConfig = apply {
        this.dismissEnterAnimation = enter
        this.dismissExitAnimation = exit
    }
}