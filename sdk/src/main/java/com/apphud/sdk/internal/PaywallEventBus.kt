package com.apphud.sdk.internal

import com.apphud.sdk.domain.PaywallEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Event bus for paywall screen events.
 * Provides communication channel between FigmaWebViewActivity and ApphudInternal.
 */
internal class PaywallEventBus {

    private val _events = MutableSharedFlow<PaywallEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    private val isActive = AtomicBoolean(false)

    /**
     * Get immutable SharedFlow for observing events.
     */
    val events: SharedFlow<PaywallEvent> = _events.asSharedFlow()

    /**
     * Activate the event bus.
     */
    fun activate() {
        isActive.set(true)
    }

    /**
     * Deactivate the event bus and clear pending events.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun deactivate() {
        isActive.set(false)
        _events.resetReplayCache()
    }

    /**
     * Emit a paywall event if the bus is active.
     * @param event The event to emit.
     */
    fun emit(event: PaywallEvent) {
        if (isActive.get()) {
            _events.tryEmit(event)
        }
    }

    /**
     * Check if the event bus is active.
     */
    fun isActive(): Boolean = isActive.get()
}