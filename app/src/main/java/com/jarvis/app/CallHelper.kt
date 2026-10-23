package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

class CallHelper(private val ctx: Context) {

    data class Contact(val name: String, val number: String)

    private val fillerSet = setOf(
        "कॉल", "काल", "call", "फोन", "फ़ोन", "phone", "dial", "डायल",
        "लगाओ", "लगा", "दो", "करो", "कर", "मिलाओ", "को", "ko", "karo",
        "lagao", "laga", "kar", "please", "plz", "जार्विस", "jarvis",
        "मुझे", "mujhe", "से", "बात", "baat", "करनी", "है", "hai", "wale", "wala"
    )

    private val callWords = listOf(
        "call", "कॉल", "काल", "फोन", "फ़ोन", "phone", "dial", "डायल", "लगाओ", "मिलाओ"
    )

    private val noWords = setOf(
        "नहीं", "नही", "nahi", "nahin", "no", "गलत", "galat", "cancel",
        "रुको", "ruko", "mat", "रद्द"
    )

    private val yesWords = setOf(
        "हाँ", "हां", "हा", "haan", "han", "ha", "yes", "सही", "sahi",
        "ठीक", "theek", "thik", "ok", "okay", "लगाओ", "lagao", "बिल्कुल"
    )

    // FIXED: Added all common conversational Hindi inflections (dusre, teesre, pehli, etc.)
    private val ordinals = listOf(
        setOf("पहला", "पहले", "pehli", "pehla", "pahla", "first", "1", "१", "एक", "one"),
        setOf("दूसरा", "दूसरे", "dusre", "dusra", "doosra", "dusri", "second", "2", "२", "दो", "two"),
        setOf("तीसरा", "तीसरे", "teesre", "teesra", "tisra", "teesri", "third", "3", "३", "तीन", "three"),
        setOf("चौथा", "चौथे", "chautha", "chauthe", "fourth", "4", "४", "चार", "four"),
        setOf("पांचवा", "पाँचवा", "panchva", "fifth", "5", "५", "पांच", "five")
    )

    private val ordinalNames = listOf("पहला", "दूसरा", "तीसरा", "चौथा", "पाँचवाँ")

    private fun tokens(t: String): List<String> =
        t.lowercase().split(" ", ",", ".", "!", "?", "।").filter { it.isNotBlank() }

    fun isCallRequest(t: String): Boolean {
        val l = t.lowercase()
        return callWords.any { l.contains(it) }
    }

    fun isNo(t: String): Boolean = tokens(t).any { it in noWords }

    fun isYes(t: String): Boolean {
        if (isNo(t)) return false
        val l = t.lowercase()
        if (l.contains("कर दो") || l.contains("kar do")) return true
        return tokens(t).any { it in yesWords }
    }

    fun cleanName(spoken: String): String =
        tokens(spoken).filter { it !in fillerSet }.joinToString(" ")

    fun digitsOnly(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val d = Character.digit(ch, 10)
            if (d >= 0) sb.append(d)
        }
        return sb.toString()
    }

    fun extractNumber(spoken: String): String? {
        val d = digitsOnly(spoken)
        return if (d.length in 7..13) d else null
    }

    fun speakableNumber(number: String): String =
        digitsOnly(number).toList().joinToString(" ")

    fun optionsText(options: List<Contact>): String {
        val sb = StringBuilder()
        for ((i, o) in options.withIndex()) {
            val ord = if (i < ordinalNames.size) ordinalNames[i] else "${i + 1}"
            sb.append("$ord ${o.name}, नंबर ${speakableNumber(o.number)}। ")
        }
        return sb.toString()
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

    private fun nameScore(sp: String, name: String): Int {
        val whole = skeleton(name)
        if (whole.isNotEmpty() && whole == sp) return 100 + whole.length
        var best = 0
        for (tok in name.split(" ", "-", ".", "_")) {
            val tk = skeleton(tok)
            if (tk.isEmpty()) continue
            if (tk == sp) best = maxOf(best, 50 + tk.length)
            else if (tk.length >= 2 && sp.contains(tk)) best = maxOf(best, 10 + tk.length)
        }
        return best
    }

    fun findContacts(spoken: String): List<Contact> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) return emptyList()
        val sp = skeleton(cleanName(spoken))
        if (sp.isEmpty()) return emptyList()
        val found = ArrayList<Pair<Contact, Int>>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    val score = nameScore(sp, name)
                    if (score > 0) found.add(Pair(Contact(name, num), score))
                }
            }
        } catch (e: Exception) {
        }
        if (found.isEmpty()) return emptyList()
        val bestTier = found.maxOf { if (it.second >= 50) 2 else 1 }
        val seen = HashSet<String>()
        val out = ArrayList<Contact>()
        val sorted = found.filter { (if (it.second >= 50) 2 else 1) == bestTier }
            .sortedByDescending { it.second }
        for ((c, _) in sorted) {
            val key = digitsOnly(c.number).takeLast(10)
            if (seen.add(key)) out.add(c)
            if (out.size >= 5) break
        }
        return out
    }

    fun choose(options: List<Contact>, spoken: String): Contact? {
        val toks = tokens(spoken)
        for (i in ordinals.indices) {
            if (i < options.size && toks.any { it in ordinals[i] }) return options[i]
        }
        val sp = skeleton(spoken)
        for (o in options) {
            val sk = skeleton(o.name)
            if (sk.isNotEmpty() && sp.contains(sk) && options.count { skeleton(it.name) == sk } == 1) {
                return o
            }
        }
        val d = digitsOnly(spoken)
        if (d.length >= 3) {
            for (o in options) {
                if (digitsOnly(o.number).endsWith(d)) return o
            }
        }
        return null
    }

    private fun sim1Handle(): PhoneAccountHandle? {
        try {
            if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED) return null
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handles = tm.callCapablePhoneAccounts
            if (handles.isEmpty()) return null
            if (Build.VERSION.SDK_INT >= 30) {
                val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
                val tel = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                for (h in handles) {
                    val subId = tel.getSubscriptionId(h)
                    val info = sm.getActiveSubscriptionInfo(subId)
                    if (info != null && info.simSlotIndex == 0) return h
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun call(number: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED) return false
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val extras = Bundle()
            val h = sim1Handle()
            if (h != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, h)
            tm.placeCall(Uri.fromParts("tel", number, null), extras)
            true
        } catch (e: Exception) {
            false
        }
    }
}
