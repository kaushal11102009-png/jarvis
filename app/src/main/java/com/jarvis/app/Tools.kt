package com.jarvis.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jarvis ke saare phone ke kaam. Har tool ek chhota text result lautata hai jo AI ko jata hai. */
class Tools(private val ctx: Context, private val launcher: AppLauncher) {

    private val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // ---------- AI ko tools ka description ----------
    fun declarations(): JSONArray = JSONArray()
        .put(decl("open_app", "Phone mein installed app kholo.",
            mapOf("app_name" to ("string" to "App ka naam, installed list se")),
            listOf("app_name")))
        .put(decl("call", "Kisi contact ya number ko call lagao.",
            mapOf("contact_or_number" to ("string" to "Contact ka naam ya phone number")),
            listOf("contact_or_number")))
        .put(decl("send_sms", "SMS likho (user ko bhejna confirm karna hoga).",
            mapOf(
                "contact_or_number" to ("string" to "Contact ka naam ya phone number"),
                "message" to ("string" to "SMS ka text")
            ),
            listOf("contact_or_number", "message")))
        .put(decl("set_alarm", "Alarm lagao.",
            mapOf(
                "hour" to ("integer" to "Ghanta, 24-hour format (0-23)"),
                "minute" to ("integer" to "Minute (0-59)"),
                "label" to ("string" to "Alarm ka naam, optional")
            ),
            listOf("hour", "minute")))
        .put(decl("set_timer", "Countdown timer lagao.",
            mapOf("seconds" to ("integer" to "Kitne second ka timer")),
            listOf("seconds")))
        .put(decl("torch", "Torch (flashlight) on ya off karo.",
            mapOf("on" to ("boolean" to "true = on, false = off")),
            listOf("on")))
        .put(decl("get_time", "Abhi ka time aur date batao.", emptyMap(), emptyList()))
        .put(decl("show_notification", "Phone par ek notification dikhao.",
            mapOf(
                "title" to ("string" to "Title"),
                "text" to ("string" to "Notification ka text")
            ),
            listOf("title", "text")))

    private fun decl(
        name: String,
        desc: String,
        props: Map<String, Pair<String, String>>,
        required: List<String>
    ): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props.isNotEmpty()) {
            val p = JSONObject()
            props.forEach { (k, v) ->
                p.put(k, JSONObject().put("type", v.first).put("description", v.second))
            }
            o.put(
                "parameters",
                JSONObject().put("type", "object").put("properties", p)
                    .put("required", JSONArray(required))
            )
        }
        return o
    }

    // ---------- Tool chalana ----------
    fun run(name: String, a: JSONObject): String = when (name) {
        "open_app" -> openApp(a.getString("app_name"))
        "call" -> call(a.getString("contact_or_number"))
        "send_sms" -> sendSms(a.getString("contact_or_number"), a.getString("message"))
        "set_alarm" -> setAlarm(a.getInt("hour"), a.getInt("minute"), a.optString("label", "Jarvis"))
        "set_timer" -> setTimer(a.getInt("seconds"))
        "torch" -> torch(a.getBoolean("on"))
        "get_time" -> SimpleDateFormat("hh:mm a, EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        "show_notification" -> notify(a.getString("title"), a.getString("text"))
        else -> "Ye tool nahi hai: $name"
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun has(perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun openApp(name: String): String {
        val label = launcher.open(name)
        return if (label != null) "$label khul gaya" else "'$name' naam ka app nahi mila"
    }

    // Naam se number dhundo (READ_CONTACTS chahiye). Number diya ho to wahi lautao.
    private fun resolveNumber(who: String): String? {
        val digits = who.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && digits.length >= who.count { it.isLetter() }) return digits
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$who%"), null
        )
        c.use { if (it != null && it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun call(who: String): String {
        val num = resolveNumber(who)
            ?: return "'$who' ka number nahi mila (contacts permission ya naam check karo)"
        val action = if (has(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        start(Intent(action, Uri.parse("tel:$num")))
        return "$who ko call lag rahi hai"
    }

    private fun sendSms(who: String, msg: String): String {
        val num = resolveNumber(who) ?: return "'$who' ka number nahi mila"
        start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", msg))
        return "SMS taiyaar hai, bas send dabana hai"
    }

    private fun setAlarm(h: Int, m: Int, label: String): String {
        start(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "Alarm %02d:%02d par lag gaya".format(h, m)
    }

    private fun setTimer(sec: Int): String {
        start(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, sec)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "$sec second ka timer shuru"
    }

    private fun torch(on: Boolean): String {
        val id = cam.cameraIdList.firstOrNull {
            cam.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Is phone mein torch nahi mili"
        cam.setTorchMode(id, on)
        return if (on) "Torch on" else "Torch off"
    }

    private fun notify(title: String, text: String): String {
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS))
            return "Notification permission nahi hai"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val n = NotificationCompat.Builder(ctx, "jarvis")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n)
        return "Notification dikha di"
    }
}
