package com.analisesex

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 501
    private val notificationRequest = 502
    private lateinit var status: TextView
    private lateinit var overlayStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(Color.rgb(10, 18, 30))
        }

        root.addView(label("SINAIS TELA", 28f, Color.WHITE, true))
        root.addView(label("Assistente flutuante para o gráfico da corretora", 15f, Color.LTGRAY))
        root.addView(label("OTC • velas M1 • expiração 1 minuto • captura a cada 5 segundos", 13f, Color.rgb(140, 195, 235)))
        root.addView(gap(12))

        root.addView(label("BOTÃO FLUTUANTE", 18f, Color.rgb(100, 190, 255), true))
        overlayStatus = label("Sobreposição não autorizada", 14f, Color.YELLOW)
        root.addView(overlayStatus)
        root.addView(action("AUTORIZAR E ATIVAR BOTÃO FLUTUANTE", Color.rgb(30, 115, 195)) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                startOverlay()
            }
        })
        root.addView(action("DESATIVAR BOTÃO FLUTUANTE", Color.rgb(75, 85, 100)) {
            stopService(Intent(this, OverlayService::class.java))
            overlayStatus.text = "Botão flutuante desligado"
        })
        root.addView(gap(14))

        root.addView(label("CAPTURA DO GRÁFICO", 18f, Color.rgb(100, 190, 255), true))
        status = label("CAPTURA DESLIGADA", 14f, Color.YELLOW)
        root.addView(status)
        root.addView(action("INICIAR CAPTURA DE TELA", Color.rgb(30, 145, 90)) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
        })
        root.addView(action("PARAR CAPTURA", Color.rgb(75, 85, 100)) {
            stopService(Intent(this, CaptureService::class.java))
            status.text = "CAPTURA DESLIGADA"
        })
        root.addView(gap(12))
        root.addView(label(
            "IMPORTANTE: esta versão instala o botão e o painel flutuante e permite testar o desenho sobre outros aplicativos. A leitura automática dos candles e a geração de sinais reais de COMPRA/VENDA ainda não estão implementadas. A marcação TESTE é apenas visual, não é recomendação de operação.",
            13f,
            Color.rgb(255, 195, 105)
        ))
        root.addView(label(
            "Quando a análise técnica estiver integrada, o painel mostrará a estratégia confirmada, o estado da leitura e o momento de entrada. Nenhuma ordem será executada automaticamente.",
            13f,
            Color.LTGRAY
        ))

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        solicitarPermissaoDeNotificacao()
    }

    override fun onResume() {
        super.onResume()
        atualizarSobreposicao()
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) startOverlay()
    }

    private fun atualizarSobreposicao() {
        if (!::overlayStatus.isInitialized) return
        val granted = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        overlayStatus.text = if (granted) "Permissão de sobreposição concedida" else "Sobreposição não autorizada"
        overlayStatus.setTextColor(if (granted) Color.rgb(80, 220, 130) else Color.YELLOW)
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
        overlayStatus.text = "Botão flutuante ativado • toque na bolinha ST"
        overlayStatus.setTextColor(Color.rgb(80, 220, 130))
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
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            status.text = "CAPTURA ATIVA • confira a notificação"
            status.setTextColor(Color.rgb(80, 220, 130))
        } else if (requestCode == captureRequest) {
            status.text = "CAPTURA NÃO AUTORIZADA"
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(5), 0, dp(6))
        }

    private fun action(value: String, color: Int, onClick: () -> Unit) =
        Button(this).apply {
            text = value
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(5); bottomMargin = dp(5)
            }
        }

    private fun gap(h: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
