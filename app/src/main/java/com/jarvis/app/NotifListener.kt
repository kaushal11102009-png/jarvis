package com.jarvis.app

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {

    data class Msg(val app: String, val sender: String, val text: String)

    companion object {
        private val skipCats = setOf(
            "transport", "progress", "service", "sys", "status", "call",
            "alarm", "navigation", "stopwatch", "workout", "location_sharing",
            "missed_call"
        )
        private val skipPkgs = setOf("android", "com.android.systemui")
        private val msgs = ArrayList<Msg>()
        private val actions = HashMap<String, Notification.Action>()

        @Volatile
        var instance: NotifListener? = null

        fun isConnected(): Boolean = instance != null

        @Synchronized
        fun summary(): String {
            if (msgs.isEmpty()) return "कोई नया संदेश नहीं है सर"
            val sb = StringBuilder("${msgs.size} संदेश हैं सर। ")
            for (m in msgs.takeLast(5)) {
                sb.append("${m.app} पर ${m.sender} ने कहा, ${m.text}। ")
            }
            return sb.toString()
        }

        @Synchronized
        fun lastSender(): String? = msgs.lastOrNull()?.sender

        @Synchronized
        fun clearAll() {
            msgs.clear()
        }

        @Synchronized
        fun addMsg(app: String, sender: String, text: String, action: Notification.Action?) {
            if (msgs.any { it.app == app && it.sender == sender && it.text == text }) return
            msgs.add(Msg(app, sender, text))
            while (msgs.size > 20) msgs.removeAt(0)
            if (action != null) actions[sender.lowercase()] = action
        }

        @Synchronized
        fun reply(sender: String, text: String): Boolean {
            val inst = instance ?: return false
            val action = actions[sender.lowercase()] ?: return false
            val ri = action.remoteInputs ?: return false
            return try {
                val intent = Intent()
                val b = Bundle()
                for (r in ri) b.putCharSequence(r.resultKey, text)
                RemoteInput.addResultsToIntent(ri, intent, b)
                action.actionIntent.send(inst, 0, intent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun appName(pkg: String): String {
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in skipPkgs) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val cat = n.category
        if (cat != null && cat in skipCats) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (title.isBlank() || text.isBlank()) return
        val action = n.actions?.firstOrNull { a ->
            a.remoteInputs != null && a.remoteInputs.isNotEmpty()
        }
        addMsg(appName(pkg), title, text, action)
    }
}
