package com.apphud.sdk.internal.data

import android.app.ActivityManager
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
        startedForUser: () -> Boolean = ::isStartedForUser,
    ) {
        if (startedForUser()) repository.onOpenedAtProcessStart()

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

    // IMPORTANCE_FOREGROUND at process start: an activity is being launched, or (rarely) the top
    // app bound the process. The latter opens a session without the user; a real open more than
    // 30 minutes later still starts its own. Pushes, receivers, services and WorkManager start
    // the process with a lower importance.
    private fun isStartedForUser(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}
