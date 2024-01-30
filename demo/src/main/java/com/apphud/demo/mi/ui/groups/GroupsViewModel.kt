package com.apphud.demo.mi.ui.groups

import androidx.lifecycle.ViewModel
import com.apphud.sdk.Apphud

class GroupsViewModel : ViewModel() {
    var items = mutableListOf<Any>()

    fun updateData() {
        val list = Apphud.permissionGroups()
        items.clear()

        list.forEach {
            items.add(it)
            it.products?.let { productsList ->
                items.addAll(productsList)
            }
        }
    }
}
