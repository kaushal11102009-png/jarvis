package com.jarvis.app

import android.content.Context
import android.hardware.camera2.CameraManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Commands(private val ctx: Context) {

    private val launcher = AppLauncher(ctx)

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun setTorch(on: Boolean): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun handle(input: String): String {
        val t = input.lowercase().trim()
        if (t.isBlank()) return "मुझे कुछ सुनाई नहीं दिया"

        if (has(t, "torch", "टॉर्च", "flashlight", "फ्लैश")) {
            return if (has(t, "off", "band", "बंद", "बन्द")) {
                if (setTorch(false)) "टॉर्च बंद कर दी" else "टॉर्च नहीं चली"
            } else {
                if (setTorch(true)) "टॉर्च चालू कर दी" else "टॉर्च नहीं चली"
            }
        }

        if (has(t, "time", "samay", "टाइम", "समय", "बजे")) {
            val now = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
            return "सर, अभी $now हुए हैं"
        }

        val opened = launcher.open(t)
        if (opened != null) return "$opened खोल रहा हूँ"

        if (has(t, "hello", "नमस्ते", "हेलो", "jarvis", "जार्विस")) {
            return "नमस्ते सर, मैं जार्विस हूँ। बताइए क्या करना है?"
        }

        return "माफ़ कीजिए सर, मुझे यह काम अभी नहीं आता"
    }
}
