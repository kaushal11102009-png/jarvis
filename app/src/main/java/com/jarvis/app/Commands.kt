package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.AlarmClock
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Commands(private val ctx: Context) {

    private val launcher = AppLauncher(ctx)
    private val caller = CallHelper(ctx)
    private val aiBrain = AiBrain(ctx)

    private enum class St {
        IDLE, CALL_NAME, CALL_PICK, CALL_CONFIRM,
        SMS_NAME, SMS_PICK, SMS_BODY, SMS_CONFIRM,
        REPLY_BODY, REPLY_CONFIRM, PLAY_STORE_CONFIRM
    }

    private var st = St.IDLE
    private var options: List<CallHelper.Contact> = emptyList()
    private var target: CallHelper.Contact? = null
    private var body = ""
    private var replyTo = ""
    private var pendingAppName = ""
    private var tries = 0

    val isWaiting: Boolean get() = st != St.IDLE

    fun reset() {
        st = St.IDLE
        options = emptyList()
        target = null
        body = ""
        replyTo = ""
        pendingAppName = ""
        tries = 0
    }

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun tokensOf(s: String): List<String> =
        s.split(" ", ",", ".", "।", "!", "?").filter { it.isNotBlank() }

    private val smsFiller = setOf(
        "sms", "message", "msg", "मैसेज", "मेसेज", "एसएमएस", "संदेश", "text", "टेक्स्ट",
        "भेजो", "भेज", "bhejo", "bhej", "दो", "do", "को", "ko", "send", "सेंड",
        "लिखो", "likho", "जार्विस", "jarvis", "करो", "kar", "कर", "एक", "ek",
        "पर", "par", "से", "please", "plz"
    )

    private fun setTorch(on: Boolean): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun cancel(): String {
        reset()
        return "ठीक है सर, रद्द कर दिया"
    }

    private fun retry(msg: String): String {
        tries++
        if (tries >= 3) return cancel()
        return msg
    }

    fun handle(input: String): String {
        val t = input.lowercase().trim()
        if (t.isBlank()) {
            return if (isWaiting) retry("मुझे सुनाई नहीं दिया सर, फिर से बोलिए")
            else "मुझे कुछ सुनाई नहीं दिया"
        }
        if (isWaiting) return handleState(t, input.trim())
        return handleNew(t, input.trim())
    }

    private fun handleNew(t: String, raw: String): String {
        if (has(t, "torch", "टॉर्च", "flashlight", "फ्लैश")) {
            return if (has(t, "off", "band", "बंद", "बन्द")) {
                if (setTorch(false)) "टॉर्च बंद कर दी" else "टॉर्च नहीं चली"
            } else {
                if (setTorch(true)) "टॉर्च चालू कर दी" else "टॉर्च नहीं चली"
            }
        }

        if (has(t, "timer", "टाइमर")) return doTimer(t)
        if (has(t, "alarm", "अलार्म")) return doAlarm(t)

        if (has(t, "notification", "नोटिफिकेशन", "नोटिफ़िकेशन") ||
            (has(t, "मैसेज", "मेसेज", "संदेश", "message") &&
                has(t, "आया", "आए", "आई", "aaya", "पढ़", "पढ", "padh", "सुना", "बता") &&
                !has(t, "भेज", "bhej", "send", "सेंड"))
        ) {
            if (has(t, "साफ", "clear", "हटा", "मिटा")) {
                NotifListener.clearAll()
                return "सारे नोटिफिकेशन साफ़ कर दिए"
            }
            if (!NotifListener.isConnected()) {
                return "नोटिफिकेशन की अनुमति नहीं है सर। जार्विस ऐप में Notification access चालू कीजिए"
            }
            return NotifListener.summary()
        }

        if (has(t, "reply", "रिप्लाई", "जवाब", "jawab")) return startReply()

        if (has(t, "sms", "एसएमएस", "message", "मैसेज", "मेसेज", "संदेश", "msg")) {
            if (has(t, "whatsapp", "व्हाट्सएप", "व्हाट्सऐप", "वॉट्सऐप")) {
                return "व्हाट्सएप पर नया मैसेज भेजना अभी नहीं आता सर, आए हुए मैसेज का जवाब देना आता है"
            }
            return startSms(t, raw)
        }

        if (caller.isCallRequest(t)) return startCall(raw)

        if (has(t, "time", "samay", "टाइम", "समय", "बजे")) {
            val now = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                .format(Calendar.getInstance().time)
            return "सर, अभी $now हुए हैं"
        }

        if (has(t, "open", "kholo", "chalao", "खोलो", "चलाओ", "खोल")) {
            val appName = t
                .replace("open", "")
                .replace("kholo", "")
                .replace("chalao", "")
                .replace("खोलो", "")
                .replace("चलाओ", "")
                .replace("खोल", "")
                .trim()

            if (appName.isNotEmpty()) {
                val success = launcher.openApp(appName)
                if (success) {
                    return "$appName खोल रहा हूँ"
                } else {
                    st = St.PLAY_STORE_CONFIRM
                    pendingAppName = appName
                    return "$appName आपके फोन में नहीं है सर। क्या इसे Play Store से ढूँढूँ?"
                }
            }
        }

        if (has(t, "hello", "नमस्ते", "हेलो", "jarvis", "जार्विस")) {
            return "नमस्ते सर, मैं जार्विस हूँ। बताइए क्या करना है?"
        }

        return "माफ़ कीजिए सर, मुझे यह काम अभी नहीं आता"
    }

    private fun handleState(t: String, raw: String): String {
        when (st) {
            St.PLAY_STORE_CONFIRM -> {
                if (caller.isYes(t)) {
                    val app = pendingAppName
                    launcher.openPlayStore(app)
                    reset()
                    return "$app Play Store पर खोजा जा रहा है"
                }
                if (caller.isNo(t)) {
                    reset()
                    return "ठीक है सर, रद्द कर दिया"
                }
                return retry("क्या Play Store से ढूँढूँ? हाँ या नहीं बोलिए")
            }
            St.CALL_NAME -> {
                if (caller.isNo(t)) return cancel()
                return startCall(raw)
            }
            St.CALL_PICK -> {
                if (caller.isNo(t)) return cancel()
                val c = caller.choose(options, t)
                if (c != null) {
                    target = c
                    st = St.CALL_CONFIRM
                    tries = 0
                    return "${c.name} को कॉल लगाऊँ?"
                }
                return retry("समझ नहीं आया सर, पहला या दूसरा बोलिए")
            }
            St.CALL_CONFIRM -> {
                if (caller.isYes(t)) {
                    val n = target
                    reset()
                    return if (n != null && caller.call(n.number)) "${n.name} को कॉल लगा रहा हूँ"
                    else "कॉल नहीं लग पाई सर, कॉल की अनुमति देखिए"
                }
                if (caller.isNo(t)) return cancel()
                return retry("कॉल लगाऊँ? हाँ या नहीं बोलिए")
            }
            St.SMS_NAME -> {
                if (caller.isNo(t)) return cancel()
                return startSms(t, raw)
            }
            St.SMS_PICK -> {
                if (caller.isNo(t)) return cancel()
                val c = caller.choose(options, t)
                if (c != null) {
                    target = c
                    tries = 0
                    return afterSmsTarget()
                }
                return retry("समझ नहीं आया सर, पहला या दूसरा बोलिए")
            }
            St.SMS_BODY -> {
                if (has(t, "cancel", "कैंसल", "रहने दो", "rehne do", "रद्द")) return cancel()
                body = raw
                st = St.SMS_CONFIRM
                tries = 0
                return "${target?.name} को मैसेज: $body। भेज दूँ?"
            }
            St.SMS_CONFIRM -> {
                if (caller.isYes(t)) {
                    val n = target
                    val b = body
                    reset()
                    return if (n != null && sendSms(n.number, b)) "मैसेज भेज दिया सर"
                    else "मैसेज नहीं भेज पाया सर, SMS की अनुमति देखिए"
                }
                if (caller.isNo(t)) return cancel()
                return retry("मैसेज भेज दूँ? हाँ या नहीं बोलिए")
            }
            St.REPLY_BODY -> {
                if (has(t, "cancel", "कैंसल", "रहने दो", "rehne do", "रद्द")) return cancel()
                body = raw
                st = St.REPLY_CONFIRM
                tries = 0
                return "$replyTo को जवाब: $body। भेज दूँ?"
            }
            St.REPLY_CONFIRM -> {
                if (caller.isYes(t)) {
                    val who = replyTo
                    val b = body
                    reset()
                    return if (NotifListener.reply(who, b)) "जवाब भेज दिया सर"
                    else "जवाब नहीं भेज पाया सर"
                }
                if (caller.isNo(t)) return cancel()
                return retry("जवाब भेज दूँ? हाँ या नहीं बोलिए")
            }
            else -> {
                reset()
                return handleNew(t, raw)
            }
        }
    }

    private fun startReply(): String {
        if (!NotifListener.isConnected()) {
            return "नोटिफिकेशन की अनुमति नहीं है सर। जार्विस ऐप में Notification access चालू कीजिए"
        }
        val s = NotifListener.lastSender() ?: return "कोई संदेश नहीं है जिसका जवाब दूँ सर"
        replyTo = s
        st = St.REPLY_BODY
        tries = 0
        return "$s को क्या जवाब दूँ?"
    }

    private fun startCall(rawInput: String): String {
        val contactsList = caller.findContacts("").joinToString(", ") { "${it.name}:${it.number}" }

        val responseJson = runBlocking {
            aiBrain.processCommand(rawInput, contactsList)
        }

        return try {
            val json = JSONObject(responseJson)
            val action = json.optString("action")
            val status = json.optString("status")
            val message = json.optString("message")

            if (action == "CALL") {
                when (status) {
                    "EXACT_MATCH" -> {
                        val targetName = json.optString("target_name")
                        val matchedContact = caller.findContacts(targetName).firstOrNull()

                        if (matchedContact != null) {
                            target = matchedContact
                            st = St.CALL_CONFIRM
                            "${matchedContact.name} को कॉल लगाऊँ?"
                        } else {
                            "संपर्क नहीं मिला सर"
                        }
                    }
                    "MULTIPLE_MATCHES" -> {
                        st = St.CALL_PICK
                        message.ifBlank { "कई संपर्क मिले, किसे कॉल लगाऊँ?" }
                    }
                    else -> {
                        reset()
                        "इस नाम का संपर्क नहीं मिला सर"
                    }
                }
            } else {
                reset()
                message
            }
        } catch (e: Exception) {
            Log.e("Jarvis", "AI error parsing call response", e)
            reset()
            "माफ़ कीजिए सर, त्रुटि: ${e.message}"
        }
    }

    private fun startSms(t: String, raw: String): String {
        var namePart = t
        var bodyPart = ""
        val markers = listOf(" कि ", " ki ", " that ")
        var cut = -1
        var mlen = 0
        for (m in markers) {
            val i = t.indexOf(m)
            if (i >= 0 && (cut < 0 || i < cut)) {
                cut = i
                mlen = m.length
            }
        }
        if (cut >= 0) {
            namePart = t.substring(0, cut)
            bodyPart = if (raw.length == t.length) raw.substring(cut + mlen).trim()
            else t.substring(cut + mlen).trim()
        }
        if (bodyPart.isNotBlank()) body = bodyPart

        val name = tokensOf(namePart).filter { it !in smsFiller }.joinToString(" ")
        val asking = st == St.SMS_NAME
        if (name.isBlank()) {
            st = St.SMS_NAME
            return if (asking) retry("मुझे नाम सुनाई नहीं दिया, फिर से बोलिए")
            else "किसे मैसेज भेजना है?"
        }
        val list = caller.findContacts(name)
        if (list.isEmpty()) {
            return if (asking) retry("इस नाम का संपर्क नहीं मिला सर, फिर से नाम बोलिए")
            else {
                reset()
                "इस नाम का संपर्क नहीं मिला सर"
            }
        }
        tries = 0
        if (list.size == 1) {
            target = list[0]
            return afterSmsTarget()
        }
        options = list
        st = St.SMS_PICK
        return "कई संपर्क मिले। " + caller.optionsText(list) + "किसे भेजूँ?"
    }

    private fun afterSmsTarget(): String {
        val n = target?.name ?: ""
        return if (body.isBlank()) {
            st = St.SMS_BODY
            "$n को क्या मैसेज भेजना है?"
        } else {
            st = St.SMS_CONFIRM
            "$n को मैसेज: $body। भेज दूँ?"
        }
    }

    private fun sendSms(number: String, text: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED) return false
        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(number, null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private val wordNums = mapOf(
        "एक" to 1, "ek" to 1, "दो" to 2, "do" to 2, "तीन" to 3, "teen" to 3,
        "चार" to 4, "char" to 4, "पांच" to 5, "पाँच" to 5, "paanch" to 5, "panch" to 5,
        "छह" to 6, "छः" to 6, "छे" to 6, "chhe" to 6, "che" to 6,
        "सात" to 7, "saat" to 7, "आठ" to 8, "aath" to 8, "नौ" to 9, "nau" to 9,
        "दस" to 10, "das" to 10, "ग्यारह" to 11, "gyarah" to 11, "बारह" to 12, "barah" to 12,
        "पंद्रह" to 15, "pandrah" to 15, "बीस" to 20, "bees" to 20,
        "तीस" to 30, "tees" to 30, "पैंतालीस" to 45
    )

    private val unitWords = setOf(
        "बजे", "baje", "बजकर", "मिनट", "minute", "minutes", "min", "mins",
        "घंटे", "घंटा", "ghante", "ghanta", "hour", "hours", "सेकंड", "second", "seconds"
    )

    private fun normalize(t: String): String {
        val digits = t.map { ch ->
            val d = Character.digit(ch, 10)
            if (d >= 0) ('0' + d) else ch
        }.joinToString("")
        val toks = digits.split(" ").filter { it.isNotBlank() }
        val out = ArrayList<String>()
        for ((i, tok) in toks.withIndex()) {
            val v = wordNums[tok]
            if (v != null && i + 1 < toks.size && toks[i + 1] in unitWords) out.add(v.toString())
            else out.add(tok)
        }
        return out.joinToString(" ")
    }

    private fun parseAlarm(t: String): Pair<Int, Int>? {
        val n = normalize(t)
        var h: Int
        var min = 0
        val m = Regex("(\\d{1,2})[:.](\\d{2})").find(n)
        if (m != null) {
            h = m.groupValues[1].toInt()
            min = m.groupValues[2].toInt()
        } else {
            val nums = Regex("\\d+").findAll(n).mapNotNull { it.value.toIntOrNull() }.toList()
            if (nums.isEmpty()) return null
            h = nums[0]
            if (has(n, "साढ़े", "साढे", "sadhe")) min = 30
            else if (has(n, "सवा", "sava")) min = 15
            else if (has(n, "पौने", "paune")) {
                h = if (h <= 1) 12 else h - 1
                min = 45
            } else if (nums.size >= 2 && nums[1] in 0..59) min = nums[1]
        }
        if (h > 23 || min > 59) return null

        val pm = has(n, "शाम", "रात", "दोपहर", "shaam", "raat", "dopahar") ||
            Regex("\\bpm\\b|\\d\\s*p\\.?m").containsMatchIn(n)
        val am = has(n, "सुबह", "सवेरे", "भोर", "subah") ||
            Regex("\\bam\\b|\\d\\s*a\\.?m").containsMatchIn(n)

        if (h <= 12 && (pm || am)) {
            if (pm && h < 12) h += 12 else if (am && h == 12) h = 0
            if (has(n, "रात", "raat") && h == 12) h = 0
        } else if (h in 1..12) {
            val c = Calendar.getInstance()
            val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            val base = (h % 12) * 60 + min
            val pick = listOf(base, base + 720)
                .map { if (it <= nowMin) it + 1440 else it }
                .minOrNull() ?: base
            h = (pick % 1440) / 60
        }
        return Pair(h, min)
    }

    private fun doAlarm(t: String): String {
        val hm = parseAlarm(t) ?: return "अलार्म किस समय का लगाऊँ सर? जैसे, सुबह सात बजे का अलार्म"
        val ok = try {
            val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hm.first)
                putExtra(AlarmClock.EXTRA_MINUTES, hm.second)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
        if (!ok) return "अलार्म नहीं लग पाया सर"
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hm.first)
        cal.set(Calendar.MINUTE, hm.second)
        val txt = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(cal.time)
        return "अलार्म लगा दिया सर, $txt के लिए"
    }

    private fun doTimer(t: String): String {
        val n = normalize(t)
        var sec = 0
        val re = Regex(
            "(\\d+)\\s*(घंटे|घंटा|घण्टे|घण्टा|hours|hour|hrs|hr|ghante|ghanta|" +
                "मिनट|minutes|minute|mins|min|minat|सेकंड|सेकण्ड|seconds|second|secs|sec)"
        )
        for (m in re.findAll(n)) {
            val v = m.groupValues[1].toIntOrNull() ?: 0
            val u = m.groupValues[2]
            sec += when {
                u.startsWith("घ") || u.startsWith("h") || u.startsWith("g") -> v * 3600
                u.startsWith("म") || u.startsWith("m") -> v * 60
                else -> v
            }
        }
        if (sec == 0 && has(n, "आधा घंटा", "आधे घंटे", "aadha ghanta")) sec = 1800
        if (sec <= 0) return "टाइमर कितने समय का लगाऊँ सर? जैसे, पाँच मिनट का टाइमर"
        sec = sec.coerceAtMost(86400)

        val ok = try {
            val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, sec)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
        if (!ok) return "टाइमर नहीं लग पाया सर"
        val parts = ArrayList<String>()
        if (sec / 3600 > 0) parts.add("${sec / 3600} घंटे")
        if ((sec % 3600) / 60 > 0) parts.add("${(sec % 3600) / 60} मिनट")
        if (sec % 60 > 0) parts.add("${sec % 60} सेकंड")
        return "${parts.joinToString(" ")} का टाइमर चालू कर दिया"
    }
}
