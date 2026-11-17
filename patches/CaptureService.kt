package com.analisesex

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var lastSampleAt = 0L
    private var lastAnalysisText = "Aguardando primeira leitura do gráfico."

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        criarCanaisDeNotificacao()

        // A notificação persistente confirma que o serviço de captura está ativo.
        val statusNotification = criarNotificacaoDeStatus()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                STATUS_NOTIFICATION_ID,
                statusNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(STATUS_NOTIFICATION_ID, statusNotification)
        }

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")

        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)

        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            PixelFormat.RGBA_8888,
            2
        )

        // Mantém a fila da captura limpa. A cada 5 segundos uma imagem fica disponível
        // para a futura etapa de análise; nesta versão ela ainda não é interpretada.
        reader?.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = SystemClock.elapsedRealtime()
            if (now - lastSampleAt >= 5_000L) {
                lastSampleAt = now
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    val result = CandleAnalyzer.analyze(bitmap)
                    lastAnalysisText = "${result.pressure} • pixels verdes: ${result.greenPixels} • vermelhos: ${result.redPixels} • colunas detectadas: ${result.candleColumns}"
                    atualizarNotificacaoDeStatus(lastAnalysisText)
                    bitmap.recycle()
                } else {
                    lastAnalysisText = "Não foi possível interpretar a captura."
                    atualizarNotificacaoDeStatus(lastAnalysisText)
                }
            }
            image.close()
        }, Handler(Looper.getMainLooper()))

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        display = projection?.createVirtualDisplay(
            "SinaisTela",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            null
        )

        return START_NOT_STICKY
    }


    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val row = y * rowStride
                for (x in 0 until width) {
                    val index = row + x * pixelStride
                    val r = buffer.get(index).toInt() and 0xff
                    val g = buffer.get(index + 1).toInt() and 0xff
                    val b = buffer.get(index + 2).toInt() and 0xff
                    val a = if (pixelStride >= 4) buffer.get(index + 3).toInt() and 0xff else 255
                    pixels[y * width + x] = Color.argb(a, r, g, b)
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }

    private fun atualizarNotificacaoDeStatus(detalhes: String) {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, STATUS_CHANNEL)
        } else {
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Sinais Tela • leitura visual ativa")
            .setContentText(detalhes)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(STATUS_NOTIFICATION_ID, notification)
    }

    private fun criarCanaisDeNotificacao() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager = getSystemService(NotificationManager::class.java)

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL,
            "Status do Sinais Tela",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mostra quando a captura de tela do Sinais Tela está ativa."
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            ALERTS_CHANNEL,
            "Alertas de sinais",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos sonoros e visuais para sinais confirmados."
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannel(statusChannel)
        manager.createNotificationChannel(alertsChannel)
    }

    private fun criarNotificacaoDeStatus(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(this, 1, openApp, flags)

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, STATUS_CHANNEL)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Sinais Tela • captura ativa")
            .setContentText("Aguardando confirmação do gráfico.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "A captura de tela está ativa. O analisador ainda está em desenvolvimento; " +
                        "nenhum sinal de COMPRA ou VENDA será inventado."
                )
            )
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    // Este método será chamado pelo analisador somente após uma confirmação real.
    // Não é acionado pela cor isolada de uma vela nem nesta versão-base.
    private fun notificarSinalConfirmado(direcao: String, detalhes: String) {
        if (direcao != "COMPRA" && direcao != "VENDA") return

        val openApp = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(this, 2, openApp, flags)

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, ALERTS_CHANNEL)
        } else {
            Notification.Builder(this)
        }

        val arrow = if (direcao == "COMPRA") "↑" else "↓"
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("SINAIS TELA • $direcao $arrow")
            .setContentText(detalhes)
            .setStyle(Notification.BigTextStyle().bigText(detalhes))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(SIGNAL_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        getSystemService(NotificationManager::class.java)
            .cancel(STATUS_NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val STATUS_CHANNEL = "sinais_tela_status"
        private const val ALERTS_CHANNEL = "sinais_tela_alertas"
        private const val STATUS_NOTIFICATION_ID = 7001
        private const val SIGNAL_NOTIFICATION_ID = 7002
    }
}
