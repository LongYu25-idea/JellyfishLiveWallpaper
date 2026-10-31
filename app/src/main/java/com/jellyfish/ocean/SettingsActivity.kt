package com.jellyfish.ocean

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** App 的啟動頁：調整設定、啟動 / 停止水母懸浮層 */
class SettingsActivity : Activity() {

    private lateinit var toggle: Button

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
            column, R.string.label_opacity, Prefs.KEY_OPACITY,
            Prefs.MIN_OPACITY, Prefs.MAX_OPACITY, Prefs.DEF_OPACITY
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

        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        column.addView(toggle, LinearLayout.LayoutParams(
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

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    private fun refreshButton() {
        toggle.text = getString(if (OverlayService.running) R.string.btn_stop else R.string.btn_start)
    }

    private fun onToggle() {
        if (OverlayService.running) {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
            toggle.postDelayed({ refreshButton() }, 400)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay_permission, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        // Android 13+ 通知權限：沒給也能運作，只是看不到通知列上的停止按鈕
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        startForegroundService(Intent(this, OverlayService::class.java))
        toggle.postDelayed({ refreshButton() }, 600)
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
}
