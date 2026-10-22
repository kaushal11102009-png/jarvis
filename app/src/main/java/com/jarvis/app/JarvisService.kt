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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.Locale

class JarvisService : Service(), RecognitionListener {

    private val handler = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sr: SpeechRecognizer? = null
    private lateinit var orb: OverlayOrb
    private lateinit var commands: Commands
    private var lastWake = 0L
    private var preparing = false
    private var busy = false

    private val watchdog = Runnable {
        if (busy) {
            tts?.stop()
            finishSession()
        }
    }

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
        orb = OverlayOrb(this)
        commands = Commands(this)
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setPitch(0.7f)
                tts?.setSpeechRate(0.9f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post { onSpeechDone(utteranceId) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { onSpeechDone(utteranceId) }
                    }
                })
                ttsReady = true
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
        if (model == null) {
            if (!preparing) {
                preparing = true
                prepare()
            }
        } else if (speechService == null && !busy) {
            startVosk()
        }
        return START_STICKY
    }

    // ---------------- Vosk model और "jarvis" सुनना ----------------

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
                val m = Model(dir.absolutePath)
                handler.post {
                    model = m
                    preparing = false
                    if (!busy) startVosk()
                }
            } catch (e: Exception) {
                handler.post {
                    preparing = false
                    updateStatus("Model error: " + e.message)
                }
            }
        }.start()
    }

    private fun startVosk() {
        val m = model ?: return
        if (speechService != null) return
        try {
            val rec = Recognizer(m, 16000.0f, "[\"jarvis\", \"[unk]\"]")
            recognizer = rec
            val ss = SpeechService(rec, 16000.0f)
            speechService = ss
            ss.startListening(this)
            updateStatus("Jarvis sun raha hai... Jarvis boliye")
        } catch (e: Exception) {
            updateStatus("Sunna shuru nahi hua: " + e.message)
        }
    }

    private fun stopVosk() {
        val ss = speechService
        val rec = recognizer
        speechService = null
        recognizer = null
        try { ss?.stop() } catch (e: Exception) {}
        try { ss?.shutdown() } catch (e: Exception) {}
        try { rec?.close() } catch (e: Exception) {}
    }

    private fun check(hyp: String?) {
        if (busy || hyp == null || !hyp.contains("jarvis")) return
        val now = System.currentTimeMillis()
        if (now - lastWake < 4000) return
        lastWake = now
        onWake()
    }

    override fun onPartialResult(hypothesis: String?) { check(hypothesis) }
    override fun onResult(hypothesis: String?) { check(hypothesis) }
    override fun onFinalResult(hypothesis: String?) { check(hypothesis) }
    override fun onError(exception: Exception?) {
        updateStatus("Error: " + exception?.message)
    }
    override fun onTimeout() {}

    // ---------------- "Jarvis" सुनने के बाद की पूरी बातचीत ----------------

    private fun armWatchdog() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, 20000)
    }

    private fun onWake() {
        busy = true
        stopVosk()
        commands.reset()
        updateStatus("Jarvis ne suna!")
        orb.show()
        speak("जी सर", "ack")
    }

    private fun speak(text: String, id: String) {
        armWatchdog()
        val t = tts
        if (t == null || !ttsReady) {
            handler.post { onSpeechDone(id) }
            return
        }
        val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (r != TextToSpeech.SUCCESS) {
            handler.post { onSpeechDone(id) }
        }
    }

    private fun onSpeechDone(id: String?) {
        if (!busy) return
        when (id) {
            "ack" -> startCommandListening()
            "reply" -> if (commands.isWaiting) startCommandListening() else finishSession()
            else -> finishSession()
        }
    }

    private fun destroyRecognizer() {
        val r = sr
        sr = null
        try { r?.cancel() } catch (e: Exception) {}
        try { r?.destroy() } catch (e: Exception) {}
    }

    private fun startCommandListening() {
        if (!busy) return
        armWatchdog()
        destroyRecognizer()
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            sr = r
            r.setRecognitionListener(object : android.speech.RecognitionListener {
                var handled = false
                override fun onReadyForSpeech(params: Bundle?) {
                    updateStatus("Jarvis: boliye...")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    orb.setLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    orb.setLevel(0f)
                }
                override fun onError(error: Int) {
                    orb.setLevel(0f)
                    if (handled) return
                    handled = true
                    handleText("")
                }
                override fun onResults(results: Bundle?) {
                    orb.setLevel(0f)
                    if (handled) return
                    handled = true
                    val t = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    handleText(t)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            r.startListening(i)
        } catch (e: Exception) {
            speak("आवाज़ पहचान शुरू नहीं हो पाई सर", "end")
        }
    }

    private fun handleText(text: String) {
        if (!busy) return
        val reply = try {
            commands.handle(text)
        } catch (e: Exception) {
            commands.reset()
            "कुछ गड़बड़ हो गई सर"
        }
        updateStatus("Jarvis: $reply")
        speak(reply, "reply")
    }

    private fun finishSession() {
        handler.removeCallbacks(watchdog)
        destroyRecognizer()
        orb.hide()
        commands.reset()
        busy = false
        lastWake = System.currentTimeMillis()
        handler.postDelayed({
            if (!busy) startVosk()
        }, 1200)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        busy = false
        destroyRecognizer()
        stopVosk()
        orb.hide()
        try { model?.close() } catch (e: Exception) {}
        tts?.shutdown()
        super.onDestroy()
    }
}
