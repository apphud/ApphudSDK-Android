package com.apphud.sdk.internal.data.local

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.apphud.sdk.internal.domain.model.LifecycleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

internal class LifecycleRepository {

    fun get(): Flow<LifecycleEvent> = callbackFlow {
        val lifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    trySend(LifecycleEvent.Started)
                }
                Lifecycle.Event.ON_STOP -> {
                    trySend(LifecycleEvent.Stopped)
                }
                else -> {}
            }
        }

        withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }

        awaitClose {
            Handler(Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleEventObserver)
            }
        }
    }
}
