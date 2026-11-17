package com.analisesex

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Camada flutuante do Sinais Tela.
 * A seta é apenas uma marcação de teste visual. Ela NÃO representa uma entrada calculada.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var marker: MarkerView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var markerParams: WindowManager.LayoutParams? = null
    private var markerVisible = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun showBubble() {
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "ST"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.rgb(18, 115, 210))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }
        val p = overlayParams(dp(54), dp(54)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }
        b.setOnClickListener { togglePanel() }
        b.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0
            private var downY = 0
            private var startX = 0
            private var startY = 0
            private var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX.toInt(); downY = e.rawY.toInt()
                        startX = p.x; startY = p.y; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX.toInt() - downX
                        val dy = e.rawY.toInt() - downY
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) moved = true
                        p.x = (startX + dx).coerceAtLeast(0)
                        p.y = (startY + dy).coerceAtLeast(0)
                        try { wm.updateViewLayout(b, p) } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                        return true
                    }
                }
                return false
            }
        })
        try { wm.addView(b, p); bubble = b } catch (_: Exception) { }
    }

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
            return
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(13, 21, 34))
                setStroke(dp(2), Color.rgb(35, 135, 220))
            }
            elevation = dp(12).toFloat()
        }
        card.addView(text("SINAIS TELA", 19f, Color.WHITE, true))
        card.addView(text("PAINEL FLUTUANTE", 11f, Color.rgb(110, 190, 255), true))
        card.addView(text("Estado: captura/análise depende da autorização e do analisador.", 13f, Color.LTGRAY))
        card.addView(text("Estratégia: Multi-estratégia", 13f, Color.WHITE))
        card.addView(text("Mercado: OTC  •  Gráfico: M1  •  Expiração: 1 min", 12f, Color.LTGRAY))
        card.addView(text("Leitura: a cada 5 segundos (quando a captura estiver ativa).", 12f, Color.LTGRAY))
        card.addView(text("Ainda não há sinal automático validado. Não opere com base na seta de teste.", 12f, Color.rgb(255, 195, 105)))
        val test = Button(this)
        test.text = if (markerVisible) "OCULTAR MARCAÇÃO DE TESTE" else "TESTAR DESENHO SOBRE A TELA"
        test.setOnClickListener {
            if (markerVisible) removeMarker() else showMarker()
            test.text = if (markerVisible) "OCULTAR MARCAÇÃO DE TESTE" else "TESTAR DESENHO SOBRE A TELA"
        }
        card.addView(test)
        card.addView(Button(this).apply {
            text = "FECHAR PAINEL"
            setOnClickListener { removePanel() }
        })
        val p = overlayParams((resources.displayMetrics.widthPixels - dp(28)).coerceAtLeast(dp(260)), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(80)
        }
        try { wm.addView(card, p); panel = card; panelParams = p } catch (_: Exception) { }
    }

    private fun showMarker() {
        if (marker != null) return
        val v = MarkerView()
        val p = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.TOP or Gravity.START
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        try { wm.addView(v, p); marker = v; markerParams = p; markerVisible = true } catch (_: Exception) { }
    }

    private fun removeMarker() {
        marker?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        marker = null
        markerParams = null
        markerVisible = false
    }

    private fun removePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        panel = null
        panelParams = null
    }

    private inner class MarkerView : View(this) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 220, 125); strokeWidth = dp(3).toFloat(); style = Paint.Style.STROKE
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 220, 125); style = Paint.Style.FILL
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = dp(15).toFloat(); typeface = Typeface.DEFAULT_BOLD
        }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val x = width * 0.72f
            val y = height * 0.48f
            c.drawLine(dp(20).toFloat(), y, width - dp(20).toFloat(), y, line)
            c.drawLine(x, y + dp(42), x, y + dp(8), line)
            c.drawLine(x, y + dp(8), x - dp(12), y + dp(22), line)
            c.drawLine(x, y + dp(8), x + dp(12), y + dp(22), line)
            c.drawRoundRect(x - dp(52).toFloat(), y - dp(42).toFloat(), x + dp(52).toFloat(), y - dp(12).toFloat(), dp(8).toFloat(), dp(8).toFloat(), fill)
            c.drawText("TESTE", x - dp(23), y - dp(22).toFloat(), label)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(5))
        }

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(w, h, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removePanel()
        removeMarker()
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
