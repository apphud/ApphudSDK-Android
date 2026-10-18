package com.apphud.sdk.internal

import com.android.billingclient.api.Purchase

/** Correlates V2 checkout recovery without relying on the device's wall clock. */
internal class PurchaseRecovery {
    class Attempt internal constructor(val productId: String, val oldToken: String?, val deferred: Boolean)

    private data class State(
        val attempt: Attempt,
        val tokens: Set<String>? = null,
        val launched: Boolean = false,
        val replacedProducts: Set<String> = emptySet(),
    )
    private var state: State? = null

    val current: Attempt? @Synchronized get() = state?.attempt

    @Synchronized
    fun begin(productId: String, oldToken: String?, deferred: Boolean = false): Attempt? {
        if (state != null) return null
        return Attempt(productId, oldToken, deferred).also { state = State(it) }
    }

    @Synchronized
    fun prepare(attempt: Attempt, tokens: Set<String>?, replacedProducts: Set<String> = emptySet()) {
        state?.takeIf { it.attempt === attempt }?.let {
            state = it.copy(tokens = tokens?.toSet(), replacedProducts = replacedProducts.toSet())
        }
    }

    @Synchronized
    fun launched(attempt: Attempt) {
        state?.takeIf { it.attempt === attempt }?.let { state = it.copy(launched = true) }
    }

    @Synchronized
    fun isCurrent(attempt: Attempt): Boolean = state?.attempt === attempt

    @Synchronized
    fun select(attempt: Attempt, purchases: List<Purchase>): Purchase? {
        val active = state?.takeIf { it.attempt === attempt && it.launched } ?: return null
        val known = active.tokens ?: return null
        return purchases.filter { matchesCandidate(attempt, it) && it.purchaseToken !in known }
            .distinctBy { it.purchaseToken }.singleOrNull()
    }

    @Synchronized
    private fun matchesCandidate(attempt: Attempt, purchase: Purchase): Boolean {
        val active = state?.takeIf { it.attempt === attempt } ?: return false
        return purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            (attempt.productId in purchase.products ||
                (attempt.deferred && active.replacedProducts.any { it in purchase.products })) &&
            purchase.purchaseToken != attempt.oldToken &&
            active.tokens?.contains(purchase.purchaseToken) != true
    }

    /** Cleanup runs before another attempt can start; user callbacks run outside this lock. */
    @Synchronized
    fun finish(attempt: Attempt, cleanup: () -> Unit): Boolean {
        if (state?.attempt !== attempt) return false
        cleanup()
        state = null
        return true
    }

    @Synchronized
    fun reset() { state = null }
}
