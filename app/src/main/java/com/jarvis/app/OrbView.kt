package com.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin

class OrbView(c: Context) : View(c) {

    var level = 0f
    private var phase = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val colors = intArrayOf(
        Color.rgb(255, 60, 160),
        Color.rgb(150, 70, 255),
        Color.rgb(40, 120, 255),
        Color.rgb(0, 220, 255),
        Color.rgb(60, 255, 190)
    )
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    fun appearAnim() {
        scaleX = 0f
        scaleY = 0f
        alpha = 0f
        animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    fun disappearAnim(end: () -> Unit) {
        animate().scaleX(0f).scaleY(0f).alpha(0f)
            .setDuration(300)
            .withEndAction { end() }
            .start()
    }

    private fun withAlpha(c: Int, a: Int): Int =
        Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f * 0.9f * (1f + level * 0.08f)
        val twoPi = 6.2831855f

        clip.reset()
        clip.addCircle(cx, cy, r, Path.Direction.CW)
        val save = cv.save()
        cv.clipPath(clip)

        p.xfermode = null
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.rgb(25, 12, 60), Color.rgb(4, 4, 18)),
            null, Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, r, p)

        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        for (i in colors.indices) {
            val dir = if (i % 2 == 0) 1f else -1f
            val a = phase * twoPi * dir + i * 1.3f
            val bx = cx + cos(a) * r * 0.38f
            val by = cy + sin(a * 1.3f + i) * r * 0.38f
            val br = r * (0.55f + level * 0.25f)
            p.shader = RadialGradient(
                bx, by, br,
                intArrayOf(withAlpha(colors[i], 200), withAlpha(colors[i], 0)),
                null, Shader.TileMode.CLAMP
            )
            cv.drawCircle(bx, by, br, p)
        }

        val core = r * 0.45f * (1f + level * 0.4f)
        p.shader = RadialGradient(
            cx, cy, core,
            intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(0, 180, 240, 255)),
            null, Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, core, p)

        p.xfermode = null
        p.shader = null
        cv.restoreToCount(save)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Color.argb(90, 255, 255, 255)
        cv.drawCircle(cx, cy, r, p)
    }
}
