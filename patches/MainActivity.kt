package com.analisesex

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 501
    private val notificationRequest = 502
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 34, 28, 28)
            setBackgroundColor(Color.rgb(10, 18, 30))
        }

        fun label(value: String, size: Float, color: Int = Color.WHITE) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(0, 8, 0, 8)
            }

        root.addView(label("SINAIS TELA", 28f))
        root.addView(label("Avisos e acompanhamento do gráfico", 15f, Color.LTGRAY))

        status = label("CAPTURA DESLIGADA", 18f, Color.YELLOW)
        root.addView(status)

        root.addView(label(
            "Quando a captura estiver ativa, o Sinais Tela ficará visível na barra de notificações. " +
                "A notificação mostrará o estado do serviço e poderá avisar sobre sinais somente quando " +
                "o analisador confirmar uma entrada.",
            15f,
            Color.WHITE
        ))

        root.addView(label(
            "Configuração prevista: OTC • gráfico de 1 minuto • expiração de 1 minuto • " +
                "leitura solicitada a cada 5 segundos.",
            14f,
            Color.LTGRAY
        ))

        root.addView(label(
            "IMPORTANTE: esta versão ainda não interpreta os candles nem gera sinais confiáveis de COMPRA ou VENDA. " +
                "A notificação atual informa apenas o estado da captura.",
            14f,
            Color.rgb(255, 205, 120)
        ))

        root.addView(Button(this).apply {
            text = "INICIAR CAPTURA"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        })

        root.addView(Button(this).apply {
            text = "PARAR CAPTURA"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "CAPTURA DESLIGADA"
            }
        })

        root.addView(label(
            "Para receber avisos, permita as notificações do Sinais Tela nas configurações do Android.",
            13f,
            Color.LTGRAY
        ))

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        solicitarPermissaoDeNotificacao()
    }

    private fun solicitarPermissaoDeNotificacao() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest)
        }
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, CaptureService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("data", data)

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            status.text = "CAPTURA ATIVA • CONFIRA A NOTIFICAÇÃO"
        } else if (requestCode == captureRequest) {
            status.text = "CAPTURA NÃO AUTORIZADA"
        }
    }
}
