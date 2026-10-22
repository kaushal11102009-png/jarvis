package com.jarvis.app

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

class OverlayOrb(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var orb: OrbView? = null
    var sizeDp = 170

    fun canShow(): Boolean = Settings.canDrawOverlays(ctx)

    fun show() {
        if (orb != null || !canShow()) return
        val d = ctx.resources.displayMetrics.density
        val px = (sizeDp * d).toInt()
        val lp = WindowManager.LayoutParams(
            px, px,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = (90 * d).toInt()
        val v = OrbView(ctx)
        try {
            wm.addView(v, lp)
            orb = v
            v.appearAnim()
        } catch (e: Exception) {
            orb = null
        }
    }

    fun setLevel(l: Float) {
        orb?.level = l
    }

    fun hide() {
        val v = orb ?: return
        orb = null
        v.disappearAnim {
            try {
                wm.removeView(v)
            } catch (e: Exception) {
            }
        }
    }
}
