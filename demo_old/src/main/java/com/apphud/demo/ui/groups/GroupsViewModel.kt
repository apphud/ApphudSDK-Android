package com.apphud.demo.ui.groups

import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud

class GroupsViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    suspend fun updateData() {
        val list = Apphud.fetchPermissionGroups()
        items.clear()

        list.forEach {
            items.add(it)
            it.productIds().let { productsList ->
                items.addAll(productsList)
            }
        }
    }
}
