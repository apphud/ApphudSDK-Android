package com.apphud.sampleapp.ui.settings

import com.apphud.sampleapp.BuildConfig
import com.apphud.sampleapp.R
import com.apphud.sampleapp.ui.utils.ResourceManager

interface ISettingsItem {
    fun title(): String?
    fun value(): String?
}

enum class SettingsInfo :ISettingsItem {
    appVersion {
        override fun title() = ResourceManager.getString(R.string.app_version)
        override fun value() = BuildConfig.VERSION_NAME
    },
    sdkVersion {
        override fun title() = ResourceManager.getString(R.string.sdk_version)
        override fun value() = "To do" //TODO Apphud SDK Version
    }
}

enum class SettingsButton :ISettingsItem {
    restore {
        override fun title() = ResourceManager.getString(R.string.restore_purchases)
        override fun value() = null
    },
    premium {
        override fun title() = ResourceManager.getString(R.string.premium_status)
        override fun value() = ResourceManager.getString(R.string.premium_value)
    }
}

class SettingsViewModel {
    val items = mutableListOf<Any>()

    init {
        updateData()
    }

    private fun updateData() {
        items.clear()

        items.add("offset")
        items.add(SettingsInfo.appVersion)
        items.add(SettingsInfo.sdkVersion)
        items.add("offset")
        items.add(SettingsButton.restore)
        items.add(SettingsButton.premium)
    }
}