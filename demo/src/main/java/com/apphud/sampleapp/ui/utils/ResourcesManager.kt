package com.apphud.sampleapp.ui.utils

import android.content.Context
import android.content.res.Resources

object ResourceManager {

    var applicationContext: Context? = null
    var resources: Resources? = null
        get(){
            if(field == null){
                field = applicationContext?.resources
            }
            return field
        }

    fun getString(id: Int, replaceText: String): String {
        val regex = "\\{\\{(.*?)\\}\\}".toRegex()
        return resources?.getText(id).toString().replace(regex, replaceText)
    }

    fun getString(id: Int): String {
        return resources?.getText(id).toString()
    }
}