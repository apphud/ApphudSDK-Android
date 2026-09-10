package com.apphud.sdk.internal

import android.os.SystemClock
import com.apphud.sdk.ApphudUtils

/**
 * Decides whether [BillingWrapper] should skip connection attempts after Google Play Billing
 * reported BILLING_UNAVAILABLE.
 *
 * Google documents BILLING_UNAVAILABLE as recoverable (outdated Play Store app, enterprise
 * policy, unsupported country, blocked Play Store since Billing Library 9), so on real devices
 * the gate closes only for [cooldownMs] and lets the SDK retry afterwards. On emulators without
 * Play Services the condition never changes, so the gate stays closed for the rest of the process
 * to avoid "Reconnection failed" / "Service not registered" warning spam from BillingClient.
 */
internal class BillingAvailabilityGate(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val isEmulator: () -> Boolean = { ApphudUtils.isEmulator() },
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    @Volatile
    private var blockedUntilMs: Long = NOT_BLOCKED

    fun isBlocked(): Boolean = now() < blockedUntilMs

    fun markUnavailable() {
        blockedUntilMs = if (isEmulator()) PERMANENT else now() + cooldownMs
    }

    fun reset() {
        blockedUntilMs = NOT_BLOCKED
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 60_000L
        private const val NOT_BLOCKED = Long.MIN_VALUE
        private const val PERMANENT = Long.MAX_VALUE
    }
}
