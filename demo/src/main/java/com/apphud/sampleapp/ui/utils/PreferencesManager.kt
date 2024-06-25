package com.apphud.sampleapp.ui.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object PreferencesManager : IStorage {
    private const val PREFS_NAME = "color_generator"
    private lateinit var prefs: SharedPreferences

    private const val PREFERENCE_COLOR = "color"
    private const val PREFERENCE_COUNT = "count"
    private const val PREFERENCE_FIRST_START = "first_start"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override var color: Int
        get() {
            return prefs.getInt(PREFERENCE_COLOR, Color.argb(255, 170, 187, 204))
        }
        set(value) {
            val editor = prefs.edit()
            editor.putInt(PREFERENCE_COLOR, value)
            editor.apply()
        }

    override var count: Int
        get() {
            return prefs.getInt(PREFERENCE_COUNT, 5)
        }
        set(value) {
            val editor = prefs.edit()
            editor.putInt(PREFERENCE_COUNT, value)
            editor.apply()
        }

    override var firstStart: Boolean
        get() {
            return prefs.getBoolean(PREFERENCE_FIRST_START, true)
        }
        set(value) {
            val editor = prefs.edit()
            editor.putBoolean(PREFERENCE_FIRST_START, value)
            editor.apply()
        }
}