package com.apphud.sdk.internal.domain.model

internal sealed class LifecycleEvent {

    object Started: LifecycleEvent()
    object Stopped: LifecycleEvent()
}