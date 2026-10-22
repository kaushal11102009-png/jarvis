package com.jarvis.app

import android.content.Context
import android.content.pm.PackageManager
import com.google.ai.client.generativeai.GenerativeModel

class AiBrain(private val context: Context) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    // फोन में इंस्टॉल ऐप्स की लिस्ट लेने का सही तरीका
    private fun getInstalledAppsList(): String {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages.joinToString(", ") { pm.getApplicationLabel(it).toString() }
    }

    suspend fun ask(prompt: String): String {
        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "कोई जवाब नहीं मिला"
        } catch (e: Exception) {
            "त्रुटि: ${e.localizedMessage}"
        }
    }
}
