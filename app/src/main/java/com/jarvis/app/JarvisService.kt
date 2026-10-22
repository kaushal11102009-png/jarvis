package com.jarvis.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.Locale

class JarvisService : Service(), RecognitionListener {

    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null
    private var lastWake = 0L
    private var preparing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )
        val n = buildNotification("Jarvis taiyaar ho raha hai...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setPitch(0.7f)
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateStatus(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (speechService == null && !preparing) {
            preparing = true
            prepare()
        }
        return START_STICKY
    }

    private fun copyAssets(path: String, out: File) {
        val list = assets.list(path) ?: return
        if (list.isEmpty()) {
            try {
                out.parentFile?.mkdirs()
                assets.open(path).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            } catch (e: Exception) {
                out.mkdirs()
            }
        } else {
            out.mkdirs()
            for (name in list) {
                copyAssets("$path/$name", File(out, name))
            }
        }
    }

    private fun prepare() {
        Thread {
            try {
                val dir = File(filesDir, "model")
                val done = File(filesDir, "model.done")
                if (!done.exists()) {
                    dir.deleteRecursively()
                    copyAssets("model", dir)
                    done.writeText("ok")
                }
                val model = Model(dir.absolutePath)
                Handler(Looper.getMainLooper()).post { startListening(model) }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    updateStatus("Model error: " + e.message)
                }
            }
        }.start()
    }

    private fun startListening(model: Model) {
        try {
            val rec = Recognizer(model, 16000.0f, "[\"jarvis\", \"[unk]\"]")
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            updateStatus("Jarvis sun raha hai... Jarvis boliye")
        } catch (e: Exception) {
            updateStatus("Sunna shuru nahi hua: " + e.message)
        }
    }

    private fun check(hyp: String?) {
        if (hyp == null || !hyp.contains("jarvis")) return
        val now = System.currentTimeMillis()
        if (now - lastWake < 4000) return
        lastWake = now
        updateStatus("Jarvis ne suna!")
        tts?.speak("जी सर", TextToSpeech.QUEUE_FLUSH, null, "wake")
    }

    override fun onPartialResult(hypothesis: String?) { check(hypothesis) }
    override fun onResult(hypothesis: String?) { check(hypothesis) }
    override fun onFinalResult(hypothesis: String?) { check(hypothesis) }
    override fun onError(exception: Exception?) {
        updateStatus("Error: " + exception?.message)
    }
    override fun onTimeout() {}

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        tts?.shutdown()
        super.onDestroy()
    }
}
