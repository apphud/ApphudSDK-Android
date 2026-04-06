package com.apphud.sdk.internal.store

import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class Store<State, Event, Effect>(
    initialState: State,
    private val reducer: (State, Event) -> Pair<State, List<Effect>>,
    private val effectHandler: suspend (Effect, dispatch: (Event) -> Unit) -> Unit,
    scope: CoroutineScope,
) {
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            for (event in events) {
                val (newState, effects) = reducer(_state.value, event)
                ApphudLog.log("Store: state → $newState")
                _state.value = newState
                for (effect in effects) {
                    launch {
                        runCatchingCancellable {
                            effectHandler(effect) { resultEvent ->
                                events.trySend(resultEvent)
                            }
                        }.onFailure { e ->
                            ApphudLog.logE("Store: unhandled effect error for $effect: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun dispatch(event: Event) {
        events.trySend(event)
    }
}
