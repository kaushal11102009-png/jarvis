package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

class AppLauncher(private val context: Context) {

    // App kholne ka main function
    fun openApp(appName: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val query = appName.lowercase().trim()
        
        val resolvedQuery = when {
            query.contains("free fire") || query.contains("freefire") -> "free fire"
            query.contains("youtube") -> "youtube"
            query.contains("canva") -> "canva"
            else -> query
        }

        for (packageInfo in packages) {
            val appLabel = pm.getApplicationLabel(packageInfo).toString().lowercase()
            val packageName = packageInfo.packageName.lowercase()

            if (appLabel.contains(resolvedQuery) || packageName.contains(resolvedQuery)) {
                val launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false // Agar app nahi mila
    }

    // Play Store kholne ke liye function
    fun openPlayStore(appName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$appName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$appName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
