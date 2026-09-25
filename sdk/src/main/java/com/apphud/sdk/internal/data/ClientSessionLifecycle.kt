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

    fun attach(repository: ClientSessionRepository) {
        // IMPORTANCE_FOREGROUND at process start means an activity is being launched; pushes,
        // receivers, services and WorkManager start the process with a lower importance.
        if (isStartedForUser()) repository.onAppOpened()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> repository.onForeground()
                    Lifecycle.Event.ON_STOP -> repository.onBackground()
                    else -> Unit
                }
            }
        )
    }

    private fun isStartedForUser(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}
