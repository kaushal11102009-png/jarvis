package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun perms(): List<String> {
        val l = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l
    }

    private fun granted(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun missing(): Array<String> =
        perms().filter { !granted(it) }.toTypedArray()

    private fun btn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(40, 80, 40, 80)

        val title = TextView(this)
        title.text = "जार्विस"
        title.setTextColor(Color.rgb(120, 220, 255))
        title.textSize = 28f
        title.gravity = Gravity.CENTER
        col.addView(title)

        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 16f
        status.setPadding(0, 40, 0, 40)
        col.addView(status)

        col.addView(btn("1. सारी अनुमतियाँ दें") { askPerms() })
        col.addView(btn("2. Display over other apps खोलें") { openOverlay() })
        col.addView(btn("3. Notification access खोलें") { openListener() })
        col.addView(btn("4. Battery: Unrestricted करें") { openBattery() })
        col.addView(btn("Jarvis चालू करें") { startJarvisService() })

        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.BLACK)
        sv.addView(col)
        setContentView(sv)

        if (missing().isNotEmpty()) askPerms() else startJarvisService()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return s != null && s.contains(packageName)
    }

    private fun batteryOk(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun mark(ok: Boolean) = if (ok) "✔" else "✘"

    private fun refresh() {
        val sb = StringBuilder()
        sb.append("${mark(granted(Manifest.permission.RECORD_AUDIO))} माइक\n")
        sb.append("${mark(granted(Manifest.permission.READ_CONTACTS))} संपर्क\n")
        sb.append(
            "${mark(granted(Manifest.permission.CALL_PHONE) &&
                granted(Manifest.permission.READ_PHONE_STATE))} कॉल\n"
        )
        sb.append("${mark(granted(Manifest.permission.SEND_SMS))} SMS\n")
        sb.append(
            "${mark(Build.VERSION.SDK_INT < 33 ||
                granted(Manifest.permission.POST_NOTIFICATIONS))} नोटिफिकेशन\n"
        )
        sb.append("${mark(Settings.canDrawOverlays(this))} Display over other apps\n")
        sb.append("${mark(listenerOn())} Notification access\n")
        sb.append("${mark(batteryOk())} Battery unrestricted\n")
        status.text = sb.toString()
    }

    private fun askPerms() {
        val m = missing()
        if (m.isEmpty()) {
            startJarvisService()
            return
        }
        requestPermissions(m, 2)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (requestCode == 2) startJarvisService()
    }

    private fun startJarvisService() {
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            try {
                startForegroundService(Intent(this, JarvisService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    private fun open(i: Intent, fallback: Intent? = null) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try {
                if (fallback != null) startActivity(fallback)
            } catch (e2: Exception) {
            }
        }
    }

    private fun openOverlay() = open(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
    )

    private fun openListener() = open(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    )

    private fun openBattery() = open(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )
}
