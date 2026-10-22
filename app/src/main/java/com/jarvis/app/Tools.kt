package com.jarvis.app

import android.content.Context

class Tools(private val context: Context) {
    private val launcher = AppLauncher(context)

    fun executeTool(toolName: String, arg: String): Boolean {
        return when (toolName.lowercase()) {
            "openapp", "open" -> launcher.openApp(arg)
            else -> false
        }
    }
}
