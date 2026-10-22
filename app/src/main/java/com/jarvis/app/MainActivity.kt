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
import android.os.Build
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
        f
