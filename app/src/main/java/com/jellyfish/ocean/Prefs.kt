package com.jellyfish.ocean

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "jellyfish_prefs"

    const val KEY_COUNT = "count"
    const val KEY_SPEED = "speed"
    const val KEY_BRIGHTNESS = "brightness"
    const val KEY_POWER_SAVE = "power_save"

    const val DEF_COUNT = 8
    const val DEF_SPEED = 100
    const val DEF_BRIGHTNESS = 100

    const val MIN_COUNT = 3
    const val MAX_COUNT = 20
    const val MIN_SPEED = 20
    const val MAX_SPEED = 200
    const val MIN_BRIGHTNESS = 30
    const val MAX_BRIGHTNESS = 150

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
