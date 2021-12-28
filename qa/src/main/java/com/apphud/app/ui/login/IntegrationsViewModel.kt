package com.apphud.app.ui.login

import androidx.lifecycle.ViewModel
import com.apphud.app.ApphudApplication
import com.apphud.app.ui.managers.Integration
import com.apphud.app.ui.storage.StorageManager

class IntegrationsViewModel : ViewModel() {
    var integrations = hashMapOf<Integration, Boolean>()
    var items = mutableListOf<Integration>()
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    fun setData() {
        integrations = storage.integrations
        items.addAll(integrations.keys.sortedBy { it.ordinal })
    }

    fun save() {
        storage.integrations = integrations
    }
}