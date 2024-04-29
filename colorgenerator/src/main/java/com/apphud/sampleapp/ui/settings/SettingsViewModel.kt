package com.apphud.sampleapp.ui.settings

import com.apphud.sampleapp.BuildConfig
import com.apphud.sampleapp.R
import com.apphud.sampleapp.ui.models.HasPremiumEvent
import com.apphud.sampleapp.ui.utils.BaseViewModel
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import com.apphud.sampleapp.ui.utils.ResourceManager
import com.apphud.sdk.managers.HeadersInterceptor
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

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
        override fun value() = HeadersInterceptor.X_SDK_VERSION
    }
}

enum class SettingsButton :ISettingsItem {
    restore {
        override fun title() = ResourceManager.getString(R.string.restore_purchases)
        override fun value() = null
    },
    premium {
        override fun title() = ResourceManager.getString(R.string.premium_status)
        override fun value() = ApphudSdkManager.isPremium()?.let {
            if(it){
               ResourceManager.getString(R.string.premium_value_yes)
            } else {
                ResourceManager.getString(R.string.premium_value_no)
            }
        }?: run {
            "${ResourceManager.getString(R.string.premium_value_yes)} / ${ResourceManager.getString(R.string.premium_value_no)}"
        }
    }
}

class SettingsViewModel :BaseViewModel(){
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

    fun restorePurchases(completionHandler :(isSuccess: Boolean) -> Unit) {
        coroutineScope.launch (errorHandler){
            ApphudSdkManager.restorePurchases { subscriptions, purchases, error ->
                mainScope.launch {
                    error?.let{
                        completionHandler(false)
                    }?: run {
                        completionHandler(true)
                        EventBus.getDefault().post(HasPremiumEvent())
                    }
                }
            }
        }
    }
}