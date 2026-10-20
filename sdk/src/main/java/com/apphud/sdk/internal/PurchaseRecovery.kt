package com.apphud.sdk.internal

import com.android.billingclient.api.Purchase
import kotlinx.coroutines.Job

/** Correlates V2 checkout recovery without relying on the device's wall clock. */
internal class PurchaseRecovery {
    class Attempt internal constructor(val productId: String, val oldToken: String?, val deferred: Boolean)

    private data class State(
        val attempt: Attempt,
        val tokens: Set<String>? = null,
        val launched: Boolean = false,
        val replacedProducts: Set<String> = emptySet(),
        val timeout: Job? = null,
        val launch: Job? = null,
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
        return purchase.purchaseState == Purchase.PurchaseState.PURCHASED && matchesListener(attempt, purchase)
    }

    /** Google listener results are authoritative even if the prelaunch history query failed. */
    @Synchronized
    fun matchesListener(attempt: Attempt, purchase: Purchase): Boolean {
        val active = state?.takeIf { it.attempt === attempt } ?: return false
        return (attempt.productId in purchase.products ||
                (attempt.deferred && active.replacedProducts.any { it in purchase.products })) &&
            purchase.purchaseToken != attempt.oldToken &&
            active.tokens?.contains(purchase.purchaseToken) != true
    }

    fun bindTimeout(attempt: Attempt, job: Job): Boolean = bind(attempt, job, timeout = true)

    fun bindLaunch(attempt: Attempt, job: Job): Boolean = bind(attempt, job, timeout = false)

    @Synchronized
    private fun bind(attempt: Attempt, job: Job, timeout: Boolean): Boolean {
        val active = state?.takeIf { it.attempt === attempt }
        if (active == null) {
            job.cancel()
            return false
        }
        (if (timeout) active.timeout else active.launch)?.cancel()
        state = if (timeout) active.copy(timeout = job) else active.copy(launch = job)
        return true
    }

    /** Cleanup runs before another attempt can start; user callbacks run outside this lock. */
    @Synchronized
    fun finish(attempt: Attempt, cleanup: () -> Unit): Boolean {
        if (state?.attempt !== attempt) return false
        state?.timeout?.cancel()
        state?.launch?.cancel()
        cleanup()
        state = null
        return true
    }

    @Synchronized
    fun reset() {
        state?.timeout?.cancel()
        state?.launch?.cancel()
        state = null
    }
}
