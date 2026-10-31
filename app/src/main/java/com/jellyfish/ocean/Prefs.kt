package com.jellyfish.ocean

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "jellyfish_prefs"

    const val KEY_COUNT = "count"
    const val KEY_SPEED = "speed"
    const val KEY_OPACITY = "opacity"
    const val KEY_POWER_SAVE = "power_save"

    const val DEF_COUNT = 6
    const val DEF_SPEED = 100
    const val DEF_OPACITY = 60

    const val MIN_COUNT = 1
    const val MAX_COUNT = 20
    const val MIN_SPEED = 20
    const val MAX_SPEED = 200
    const val MIN_OPACITY = 10
    const val MAX_OPACITY = 100

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
