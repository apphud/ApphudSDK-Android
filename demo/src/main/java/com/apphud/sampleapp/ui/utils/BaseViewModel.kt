package com.apphud.sampleapp.ui.utils

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class BaseViewModel: ViewModel() {
    val mainScope = CoroutineScope(Dispatchers.Main)
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val errorHandler = CoroutineExceptionHandler { _, error ->
        error.message?.let {
            Log.e("BaseViewModel", it)
        }
    }
}