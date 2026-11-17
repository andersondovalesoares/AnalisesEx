package com.analisesex

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Leitor visual experimental. Detecta pixels de candles em uma região central
 * do gráfico. Não transforma uma cor isolada em sinal operacional.
 */
object CandleAnalyzer {
    data class Result(
        val greenPixels: Int,
        val redPixels: Int,
        val candleColumns: Int,
        val pressure: String,
        val confidence: Int
    )

    fun analyze(bitmap: Bitmap): Result {
        val w = bitmap.width
        val h = bitmap.height
        val left = (w * 0.08f).toInt()
        val right = (w * 0.92f).toInt()
        val top = (h * 0.12f).toInt()
        val bottom = (h * 0.72f).toInt()
        val greenByColumn = IntArray(right - left)
        val redByColumn = IntArray(right - left)
        var green = 0
        var red = 0

        // Amostragem reduzida para manter o processamento leve no aparelho.
        val stepX = (w / 420).coerceAtLeast(1)
        val stepY = (h / 700).coerceAtLeast(1)
        var x = left
        while (x < right) {
            var y = top
            while (y < bottom) {
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val isGreen = g > 105 && g > r * 1.28 && g > b * 1.12
                val isRed = r > 125 && r > g * 1.28 && r > b * 1.18
                val col = x - left
                if (isGreen) { green++; greenByColumn[col]++ }
                if (isRed) { red++; redByColumn[col]++ }
                y += stepY
            }
            x += stepX
        }

        var activeColumns = 0
        var inRun = false
        for (i in greenByColumn.indices step stepX) {
            val active = greenByColumn[i] + redByColumn[i] >= 2
            if (active && !inRun) activeColumns++
            inRun = active
        }

        val total = green + red
        val greenShare = if (total == 0) 0f else green.toFloat() / total
        val redShare = 1f - greenShare
        val pressure = when {
            total < 80 || activeColumns < 3 -> "GRÁFICO NÃO IDENTIFICADO"
            greenShare >= 0.62f -> "PRESSÃO COMPRADORA"
            redShare >= 0.62f -> "PRESSÃO VENDEDORA"
            else -> "MERCADO MISTO"
        }
        val dominance = (kotlin.math.abs(greenShare - 0.5f) * 200).toInt().coerceIn(0, 100)
        val confidence = if (total < 80) 0 else dominance
        return Result(green, red, activeColumns, pressure, confidence)
    }
}
