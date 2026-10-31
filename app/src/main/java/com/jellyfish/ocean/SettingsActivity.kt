package com.jellyfish.ocean

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** 同時是桌布的設定頁，也是 App 的啟動頁 */
class SettingsActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }

        column.addView(text(getString(R.string.app_name), 28f, Color.WHITE))
        column.addView(text(getString(R.string.subtitle), 14f, 0xFF9DB7FF.toInt()).also {
            it.setPadding(0, dp(4), 0, dp(24))
        })

        addSlider(
            column, R.string.label_count, Prefs.KEY_COUNT,
            Prefs.MIN_COUNT, Prefs.MAX_COUNT, Prefs.DEF_COUNT
        )
        addSlider(
            column, R.string.label_speed, Prefs.KEY_SPEED,
            Prefs.MIN_SPEED, Prefs.MAX_SPEED, Prefs.DEF_SPEED
        )
        addSlider(
            column, R.string.label_brightness, Prefs.KEY_BRIGHTNESS,
            Prefs.MIN_BRIGHTNESS, Prefs.MAX_BRIGHTNESS, Prefs.DEF_BRIGHTNESS
        )

        column.addView(Switch(this).apply {
            text = getString(R.string.label_power_save)
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(Prefs.KEY_POWER_SAVE, false)
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(Prefs.KEY_POWER_SAVE, checked).apply()
            }
        })

        column.addView(Button(this).apply {
            text = getString(R.string.btn_set_wallpaper)
            setOnClickListener { openWallpaperPicker() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        column.addView(text(getString(R.string.hint), 13f, 0xFF8A9CCB.toInt()).also {
            it.setPadding(0, dp(20), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF050B24.toInt())
            isFillViewport = true
            addView(column)
        })
    }

    private fun text(value: String, sizeSp: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.START
    }

    private fun addSlider(
        parent: LinearLayout, labelRes: Int, key: String, min: Int, max: Int, def: Int
    ) {
        val prefs = Prefs.get(this)
        val current = prefs.getInt(key, def).coerceIn(min, max)
        val label = text(getString(labelRes, current), 16f, Color.WHITE).also {
            it.setPadding(0, dp(12), 0, 0)
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val value = p + min
                    label.text = getString(labelRes, value)
                    if (fromUser) prefs.edit().putInt(key, value).apply()
                }

                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        parent.addView(label)
        parent.addView(bar)
    }

    private fun openWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, JellyfishWallpaperService::class.java)
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
