package com.apphud.sdk.internal.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Feeds the process lifecycle to [ClientSessionRepository]. Must run on the main thread at
 * process start, before the SDK's first request.
 */
internal object ClientSessionLifecycle {

    fun attach(
        repository: ClientSessionRepository,
        lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    ) {
        repository.onProcessStart()

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> repository.onForeground()
                    Lifecycle.Event.ON_STOP -> repository.onBackground()
                    else -> Unit
                }
            }
        )
    }
}
