package com.analisesex

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class DeliveryAdvisorService : AccessibilityService() {
    private data class Offer(val value: Double, val km: Double, val minutes: Int, val timeWasRead: Boolean)
    private data class Evaluation(
        val value: Double, val km: Double, val minutes: Int,
        val perKm: Double, val perHour: Double, val profit: Double,
        val status: String, val title: String, val color: Int, val details: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var speech: TextToSpeech? = null
    private var lastFingerprint = ""
    private var lastOfferAt = 0L
    private var lastScanAt = 0L
    private val moneyRegex = Regex("""R\s*\$\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("""(?<![0-9])([0-9]{1,3}(?:[.,][0-9]{1,2})?)\s*(?:km|quil[oô]metros?)""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""(?<![0-9])([0-9]{1,3})\s*(?:min|minutos)""", RegexOption.IGNORE_CASE)
    private val hideOverlayTask = Runnable { removerCartao() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        criarCanalNotificacao()
        speech = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                speech?.language = Locale("pt", "BR")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() == packageName) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanAt < 900) return
        lastScanAt = now

        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return
        val pieces = linkedSetOf<String>()
        coletarTexto(root, pieces)
        val screenText = pieces.joinToString(" ").trim()
        if (screenText.isBlank()) return

        val lower = screenText.lowercase(Locale("pt", "BR"))
        val palavrasDeOferta = listOf("aceitar", "entrega", "corrida", "pedido", "oferta", "coleta", "retirada", "restaurante", "destino", "ifood", "uber", "99food", "keeta")
        if (palavrasDeOferta.none { lower.contains(it) }) return

        val offer = lerOferta(screenText) ?: return
        val fingerprint = String.format(Locale.US, "%.2f|%.2f|%d", offer.value, offer.km, offer.minutes)
        val elapsed = now - lastOfferAt
        if (fingerprint == lastFingerprint && elapsed < 25000L) return
        lastFingerprint = fingerprint
        lastOfferAt = now

        val result = avaliar(offer)
        mostrarCartao(result)
        publicarNotificacao(result)
        falarResultado(result)
    }

    private fun coletarTexto(node: AccessibilityNodeInfo?, output: MutableSet<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        val description = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrBlank()) output.add(text)
        if (!description.isNullOrBlank()) output.add(description)
        for (index in 0 until node.childCount) {
            try { coletarTexto(node.getChild(index), output) } catch (_: Exception) { }
        }
    }

    private fun lerOferta(text: String): Offer? {
        val value = moneyRegex.find(text)?.groupValues?.getOrNull(1)?.let { decimal(it) } ?: return null
        val distances = distanceRegex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.let { raw -> decimal(raw) } }
            .filter { it > 0.0 }
            .distinct()
            .take(3)
            .toList()
        if (distances.isEmpty()) return null

        // Quando a oferta mostra coleta e entrega separadas, soma as distâncias únicas encontradas.
        val km = distances.sum()
        val times = timeRegex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .take(2)
            .toList()
        val prefs = getSharedPreferences("preferencias_entrega", MODE_PRIVATE)
        val speed = prefs.getString("velocidade", "20")?.let { decimal(it) }?.takeIf { it > 0.0 } ?: 20.0
        val minutes = if (times.isNotEmpty()) times.sum() else ((km / speed) * 60.0 + 5.0).roundToInt().coerceAtLeast(1)
        return Offer(value, km, minutes, times.isNotEmpty())
    }

    private fun avaliar(offer: Offer): Evaluation {
        val prefs = getSharedPreferences("preferencias_entrega", MODE_PRIVATE)
        val minimumKm = prefs.getString("meta_km", "2,00")?.let { decimal(it) } ?: 2.0
        val minimumHour = prefs.getString("meta_hora", "30,00")?.let { decimal(it) } ?: 30.0
        val costKm = prefs.getString("custo_km", "0,15")?.let { decimal(it) } ?: 0.15
        val perKm = offer.value / offer.km
        val perHour = offer.value / (offer.minutes.toDouble() / 60.0)
        val profit = offer.value - (offer.km * costKm)
        val goodKm = perKm >= minimumKm
        val goodHour = perHour >= minimumHour
        val status: String
        val title: String
        val color: Int
        when {
            profit <= 0.0 || (!goodKm && !goodHour) -> {
                status = "RUIM"
                title = "NÃO COMPENSA"
                color = Color.rgb(220, 65, 65)
            }
            goodKm && goodHour -> {
                status = "BOA"
                title = "VALE A PENA"
                color = Color.rgb(35, 170, 95)
            }
            else -> {
                status = "ATENÇÃO"
                title = "AVALIE A OFERTA"
                color = Color.rgb(224, 160, 35)
            }
        }
        val timeLabel = if (offer.minutes > 0) offer.minutes.toString() + " min" else "não informado"
        val details = "R$ " + money(perKm) + "/km  •  R$ " + money(perHour) + "/h\n" +
            "Lucro estimado: R$ " + money(profit) + "\n" +
            "Distância considerada: " + decimalLabel(offer.km) + " km  •  Tempo: " + timeLabel +
            if (offer.timeWasRead) "" else " (estimado)"
        return Evaluation(offer.value, offer.km, offer.minutes, perKm, perHour, profit, status, title, color, details)
    }

    private fun mostrarCartao(result: Evaluation) {
        handler.post {
            removerCartao()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.rgb(20, 28, 40))
                    setStroke(dp(3), result.color)
                }
                elevation = dp(8).toFloat()
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val heading = TextView(this).apply {
                text = "SINAIS TELA  •  " + result.title
                textSize = 16f
                setTextColor(result.color)
                typeface = Typeface.DEFAULT_BOLD
            }
            val close = TextView(this).apply {
                text = "  ×"
                textSize = 24f
                setTextColor(Color.WHITE)
                setOnClickListener { removerCartao() }
            }
            header.addView(heading, LinearLayout.LayoutParams(0, dp(34), 1f))
            header.addView(close)
            card.addView(header)
            val amount = TextView(this).apply {
                text = "Oferta: R$ " + money(result.value)
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            card.addView(amount)
            val metrics = TextView(this).apply {
                text = result.details
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(6), 0, 0)
            }
            card.addView(metrics)
            val warning = TextView(this).apply {
                text = "Estimativa baseada nos dados visíveis. Confira antes de aceitar."
                textSize = 11f
                setTextColor(Color.rgb(190, 200, 215))
                setPadding(0, dp(8), 0, 0)
            }
            card.addView(warning)

            val width = (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(260))
            val params = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(28)
            }
            try {
                windowManager?.addView(card, params)
                overlay = card
                handler.removeCallbacks(hideOverlayTask)
                handler.postDelayed(hideOverlayTask, 22000L)
            } catch (_: Exception) {
                overlay = null
            }
        }
    }

    private fun publicarNotificacao(result: Evaluation) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val detail = "R$ " + money(result.perKm) + "/km • R$ " + money(result.perHour) + "/h • lucro R$ " + money(result.profit)
        val pending = PendingIntent.getActivity(
            this, 11, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sinais Tela • " + result.title)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
        if (Build.VERSION.SDK_INT < 26) builder.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Avaliação de entregas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos quando uma oferta de entrega é avaliada"
                enableVibration(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun falarResultado(result: Evaluation) {
        val prefs = getSharedPreferences("preferencias_entrega", MODE_PRIVATE)
        if (!prefs.getBoolean("voz", false)) return
        val text = result.title + ". " +
            money(result.value) + " reais. " +
            money(result.perKm) + " reais por quilômetro. " +
            money(result.perHour) + " reais por hora. " +
            "Lucro estimado de " + money(result.profit) + " reais."
        handler.post {
            try { speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "oferta_atual") } catch (_: Exception) { }
        }
    }

    private fun removerCartao() {
        handler.removeCallbacks(hideOverlayTask)
        val current = overlay ?: return
        try { windowManager?.removeView(current) } catch (_: Exception) { }
        overlay = null
    }

    private fun decimal(value: String): Double = value.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
    private fun money(value: Double): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)
    private fun decimalLabel(value: Double): String = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }.format(value)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() { removerCartao() }

    override fun onDestroy() {
        removerCartao()
        handler.removeCallbacksAndMessages(null)
        speech?.stop()
        speech?.shutdown()
        speech = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "avaliacao_entregas"
        private const val NOTIFICATION_ID = 410
    }
}
