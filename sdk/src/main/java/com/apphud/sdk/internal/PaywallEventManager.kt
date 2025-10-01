package com.apphud.sdk.internal

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.domain.PaywallEvent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Manager for paywall event bus.
 * Provides unified API for event handling between ViewModel and ApphudInternal.
 */
internal class PaywallEventManager {

    private val eventBus = PaywallEventBus()

    /**
     * Get SharedFlow for observing events (for ApphudInternal).
     */
    val events: SharedFlow<PaywallEvent> = eventBus.events

    /**
     * Activate event handling (called from ViewModel).
     */
    fun activate() {
        ApphudLog.logI("[PaywallEventManager] Activating event bus")
        eventBus.activate()
    }

    /**
     * Deactivate event handling (called from ViewModel).
     */
    fun deactivate() {
        ApphudLog.logI("[PaywallEventManager] Deactivating event bus")
        eventBus.deactivate()
    }

    /**
     * Emit a paywall event (called from ViewModel).
     * Safe to call - will only emit if active.
     */
    fun emitEvent(event: PaywallEvent) {
        eventBus.emit(event)
    }

    /**
     * Check if event bus is active.
     */
    fun isActive(): Boolean = eventBus.isActive()
}