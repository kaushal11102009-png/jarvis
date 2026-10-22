package com.jarvis.app

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CircleView(c: Context) : View(c) {
    var level = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f

    init {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.22f
        for (i in 0..2) {
            val f = (phase + i / 3f) % 1f
            val r = base * (1f + f * 0.9f + level * 0.3f)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.color = Color.argb(((1f - f) * 160).toInt(), 0, 200, 255)
            cv.drawCircle(cx, cy, r, p)
        }
        val core = base * (1f + level * 0.3f)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, core,
            intArrayOf(
                Color.argb(255, 200, 250, 255),
                Color.argb(255, 0, 160, 255),
                Color.argb(255, 0, 60, 160)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, core, p)
        p.shader = null
    }
}

class MainActivity : Activity() {
    private lateinit var circle: CircleView
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var sr: SpeechRecognizer? = null
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        circle = CircleView(this)
        root.addView(circle, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            text = "टैप करके बोलिए"
            setTextColor(Color.rgb(120, 220, 255))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 120)
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnClickListener { listen() }
        setContentView(root)

        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setPitch(0.7f)
                tts?.setSpeechRate(0.9f)
            }
        }
    }

    private fun say(t: String) {
        status.text = t
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "सुन रहा हूँ..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                circle.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { circle.level = 0f }
            override fun onError(error: Int) {
                circle.level = 0f
                status.text = "सुन नहीं पाया, दोबारा टैप कीजिए"
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                handle(t.lowercase())
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        r.startListening(i)
    }

    private fun setTorch(on: Boolean) {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
            torchOn = on
        } catch (e: Exception) {
            status.text = "टॉर्च नहीं चली"
        }
    }

    private fun openApp(pkg: String, fallbackUrl: String) {
        val li = packageManager.getLaunchIntentForPackage(pkg)
        if (li != null) startActivity(li)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
    }

    private fun handle(t: String) {
        if (t.isBlank()) { say("मुझे कुछ सुनाई नहीं दिया"); return }
        when {
            has(t, "torch", "टॉर्च", "flashlight", "फ्लैश") -> {
                if (has(t, "off", "band", "बंद", "बन्द")) { setTorch(false); say("टॉर्च बंद कर दी") }
                else { setTorch(true); say("टॉर्च चालू कर दी") }
            }
            has(t, "time", "samay", "टाइम", "समय", "बजे") -> {
                val now = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
                say("सर, अभी $now हुए हैं")
            }
            has(t, "youtube", "यूट्यूब") -> {
                say("यूट्यूब खोल रहा हूँ")
                openApp("com.google.android.youtube", "https://www.youtube.com")
            }
            has(t, "whatsapp", "व्हाट्सएप", "वॉट्सएप", "व्हाट्सऐप") -> {
                say("व्हाट्सएप खोल रहा हूँ")
                openApp("com.whatsapp", "https://web.whatsapp.com")
            }
            has(t, "google", "गूगल") -> {
                say("गूगल खोल रहा हूँ")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
            }
            has(t, "hello", "hi", "नमस्ते", "हेलो", "जार्विस", "jarvis") ->
                say("नमस्ते सर, मैं जार्विस हूँ। बताइए क्या करना है?")
            else -> say("माफ़ कीजिए सर, मुझे यह काम अभी नहीं आता")
        }
    }

    override fun onDestroy() {
        sr?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
