package com.apphud.demo.ui.utils

import android.util.Log
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class BaseEvent

open class BaseFragment : Fragment() {

    val mainScope = CoroutineScope(Dispatchers.Main)
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val errorHandler = CoroutineExceptionHandler { _, error ->
        error.message?.let {
            Log.d("BaseFragment", it)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: BaseEvent) {

    }
}