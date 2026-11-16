package com.analisesex

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private val captureRequest = 501
    private lateinit var captureStatus: TextView
    private lateinit var deliveryStatus: TextView
    private lateinit var minPerKm: EditText
    private lateinit var minPerHour: EditText
    private lateinit var costPerKm: EditText
    private lateinit var averageSpeed: EditText
    private lateinit var voiceEnabled: CheckBox

    private val prefs by lazy { getSharedPreferences("preferencias_entrega", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        solicitarPermissaoNotificacoes()

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(Color.rgb(13, 19, 29))
        }
        scroll.addView(box)
        setContentView(scroll)

        box.addView(label("SINAIS TELA", 28f, Color.WHITE, true))
        box.addView(label("Assistente de gráfico e entregas", 15f, Color.LTGRAY))
        box.addView(space(18))

        box.addView(section("ASSISTENTE DE ENTREGAS"))
        box.addView(label("Avalia ofertas visíveis nos aplicativos de entrega e mostra um cartão flutuante e uma notificação com a recomendação.", 14f, Color.LTGRAY))
        box.addView(space(10))
        deliveryStatus = label("Assistente de entregas: ainda não ativado", 14f, Color.YELLOW, true)
        box.addView(deliveryStatus)
        box.addView(actionButton("ATIVAR / CONFIGURAR ASSISTENTE", Color.rgb(31, 116, 196)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        box.addView(label("Na tela de Acessibilidade do Android, procure “Sinais Tela” e ative o serviço. A leitura é feita no aparelho; nenhuma oferta é aceita automaticamente.", 12f, Color.LTGRAY))
        box.addView(space(18))

        box.addView(section("SUAS METAS DE ENTREGA"))
        box.addView(label("Valores iniciais para ajustar à sua região e ao seu veículo.", 13f, Color.LTGRAY))
        minPerKm = field(box, "Meta mínima por quilômetro (R$)", prefs.getString("meta_km", "2,00") ?: "2,00")
        minPerHour = field(box, "Meta mínima por hora (R$)", prefs.getString("meta_hora", "30,00") ?: "30,00")
        costPerKm = field(box, "Custo estimado por quilômetro (R$)", prefs.getString("custo_km", "0,15") ?: "0,15")
        averageSpeed = field(box, "Velocidade média para estimar tempo (km/h)", prefs.getString("velocidade", "20") ?: "20")
        voiceEnabled = CheckBox(this).apply {
            text = "Falar o resultado em voz alta"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("voz", false)
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(80, 180, 255))
        }
        box.addView(voiceEnabled)
        box.addView(actionButton("SALVAR METAS", Color.rgb(33, 150, 92)) { salvarMetas() })
        box.addView(space(18))

        box.addView(section("ANÁLISE DE GRÁFICO"))
        box.addView(label("A captura de tela está disponível como base técnica. A leitura automática de candles e sinais de compra/venda ainda não está implementada de forma confiável.", 13f, Color.LTGRAY))
        captureStatus = label("Captura de tela desligada", 14f, Color.YELLOW, true)
        box.addView(captureStatus)
        box.addView(actionButton("INICIAR CAPTURA DE TELA", Color.rgb(31, 116, 196)) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
        })
        box.addView(actionButton("PARAR CAPTURA", Color.rgb(80, 88, 102)) {
            stopService(Intent(this, CaptureService::class.java))
            captureStatus.text = "Captura de tela desligada"
        })
        box.addView(space(12))
        box.addView(label("Importante: o assistente calcula estimativas a partir das informações que o aplicativo de entrega deixa acessíveis. Confira a distância e as condições da oferta antes de decidir.", 12f, Color.LTGRAY))
    }

    override fun onResume() {
        super.onResume()
        if (::deliveryStatus.isInitialized) {
            deliveryStatus.text = if (acessibilidadeAtiva()) {
                "Assistente de entregas: ATIVO"
            } else {
                "Assistente de entregas: ainda não ativado"
            }
            deliveryStatus.setTextColor(if (acessibilidadeAtiva()) Color.rgb(92, 220, 135) else Color.YELLOW)
        }
    }

    private fun acessibilidadeAtiva(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val serviceName = ComponentName(this, DeliveryAdvisorService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(serviceName, ignoreCase = true) || it.contains(DeliveryAdvisorService::class.java.name, ignoreCase = true) }
    }

    private fun salvarMetas() {
        val km = numero(minPerKm.text.toString())
        val hora = numero(minPerHour.text.toString())
        val custo = numero(costPerKm.text.toString())
        val velocidade = numero(averageSpeed.text.toString())
        if (km <= 0.0 || hora <= 0.0 || custo < 0.0 || velocidade <= 0.0) {
            Toast.makeText(this, "Confira os valores. As metas e a velocidade devem ser maiores que zero.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit()
            .putString("meta_km", minPerKm.text.toString())
            .putString("meta_hora", minPerHour.text.toString())
            .putString("custo_km", costPerKm.text.toString())
            .putString("velocidade", averageSpeed.text.toString())
            .putBoolean("voz", voiceEnabled.isChecked)
            .apply()
        Toast.makeText(this, "Metas salvas!", Toast.LENGTH_SHORT).show()
    }

    private fun solicitarPermissaoNotificacoes() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 700)
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
            captureStatus.text = "Captura autorizada • sessão ativa"
            captureStatus.setTextColor(Color.rgb(92, 220, 135))
        }
    }

    private fun field(parent: LinearLayout, title: String, value: String): EditText {
        parent.addView(label(title, 13f, Color.LTGRAY, true))
        val edit = EditText(this).apply {
            setText(value)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            singleLine = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.rgb(27, 37, 51), Color.rgb(61, 78, 100))
        }
        parent.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(10) })
        return edit
    }

    private fun section(text: String): TextView = label(text, 17f, Color.rgb(100, 190, 255), true)
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(5))
        }

    private fun actionButton(text: String, color: Int, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            background = rounded(color, color)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun space(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun numero(value: String): Double = value.trim().replace(",", ".").toDoubleOrNull() ?: -1.0
}
