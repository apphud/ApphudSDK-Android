package com.apphud.sampleapp.ui.main

import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.apphud.sampleapp.ui.utils.PreferencesManager
import com.apphud.sampleapp.ui.utils.ResourceManager
import java.util.Random
import com.apphud.sampleapp.R
import com.apphud.sampleapp.ui.utils.PurchaseManager


class MainViewModel : ViewModel() {

    private val _hexColor = MutableLiveData<String>()
    val hexColor: LiveData<String> = _hexColor
    var color :Int = PreferencesManager.color
    var count :Int = PreferencesManager.count
    var isUnlimited :Boolean = false
        get() {
            return PurchaseManager.isPremium()?: false
        }

    var showPaywall: (()->Unit)? = null

    init {
        _hexColor.value = String.format("#%06X", 0xFFFFFF and color)
    }

    fun generateColor(){
        if(isUnlimited){
            generate()
        } else {
            if(count > 0){
                count--
                PreferencesManager.count = count
                generate()
            } else {
                showPaywall?.invoke()
            }
        }
    }

    fun getCounterString() :String {
       return if(isUnlimited){
           ResourceManager.getString(R.string.onboarding_title_3)
        } else {
           ResourceManager.getString(R.string.you_have_text, count.toString())
        }
    }

    private fun generate(){
        val rnd = Random()
        color = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        _hexColor.value = String.format("#%06X", 0xFFFFFF and color)
        PreferencesManager.color = color

        addUserProperty()
    }

    private fun addUserProperty(){
        hexColor.value?.let{
            PurchaseManager.addUserProperty(it, count)
        }
    }
}