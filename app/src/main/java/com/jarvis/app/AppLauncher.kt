package com.jarvis.app

import android.content.Context
import android.content.Intent

class AppLauncher(private val ctx: Context) {

    data class AppInfo(val label: String, val pkg: String)

    fun installedApps(): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
    }

    private fun roman(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            val r = when (ch) {
                'अ', 'आ', 'ा' -> "a"
                'इ', 'ई', 'ि', 'ी' -> "i"
                'उ', 'ऊ', 'ु', 'ू' -> "u"
                'ए', 'ऐ', 'े', 'ै' -> "e"
                'ओ', 'औ', 'ऑ', 'ो', 'ौ', 'ॉ' -> "o"
                'ऋ', 'ृ' -> "ri"
                'ं', 'ँ' -> "n"
                'ः' -> "h"
                '्', '़' -> ""
                'क', 'ख' -> "k"
                'ग', 'घ' -> "g"
                'ङ', 'ञ', 'ण', 'न' -> "n"
                'च', 'छ' -> "c"
                'ज', 'झ' -> "j"
                'ट', 'ठ', 'त', 'थ' -> "t"
                'ड', 'ढ', 'द', 'ध' -> "d"
                'प' -> "p"
                'फ' -> "f"
                'ब', 'भ' -> "b"
                'म' -> "m"
                'य' -> "y"
                'र' -> "r"
                'ल' -> "l"
                'व' -> "v"
                'श', 'ष', 'स' -> "s"
                'ह' -> "h"
                else -> ch.toString()
            }
            sb.append(r)
        }
        return sb.toString()
    }

    private fun skeleton(s: String): String {
        var t = roman(s).filter { it in 'a'..'z' }
        t = t.replace("ph", "f").replace("x", "ks")
            .replace('c', 'k').replace('q', 'k').replace('z', 'j')
        val sb = StringBuilder()
        for (ch in t) {
            if (ch in "aeiouyhwv") continue
            if (sb.isNotEmpty() && sb.last() == ch) continue
            sb.append(ch)
        }
        return sb.toString()
    }

    fun find(spoken: String): AppInfo? {
        val sp = skeleton(spoken)
        val tokens = spoken.lowercase().split(" ", "\n")
            .filter { it.isNotBlank() }
            .map { skeleton(it) }
        var best: AppInfo? = null
        var bestScore = 0
        for (a in installedApps()) {
            val ls = skeleton(a.label)
            val ok = if (ls.length >= 3) sp.contains(ls)
            else ls.length == 2 && tokens.contains(ls)
            if (ok && ls.length > bestScore) {
                best = a
                bestScore = ls.length
            }
        }
        return best
    }

    fun open(spoken: String): String? {
        val app = find(spoken) ?: return null
        return try {
            val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return null
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            app.label
        } catch (e: Exception) {
            null
        }
    }
}
