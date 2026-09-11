package ar.edu.utn.frsfco.finanzas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Anillo que reparte el gasto del mes entre las categorías.
 *
 * Está dibujado a mano con Canvas en lugar de sumar una biblioteca de gráficos, que
 * para un solo gráfico traería mucho peso y poco a cambio.
 */
class GraficoAnillo @JvmOverloads constructor(
    contexto: Context,
    atributos: AttributeSet? = null,
    estilo: Int = 0
) : View(contexto, atributos, estilo) {

    /** Cada porción, con su color y cuánto suma. */
    data class Porcion(val color: Int, val monto: Double)

    private var porciones: List<Porcion> = emptyList()

    private val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val marco = RectF()

    fun mostrar(nuevas: List<Porcion>) {
        porciones = nuevas.filter { it.monto > 0 }
        invalidate()
    }

    override fun onDraw(lienzo: Canvas) {
        super.onDraw(lienzo)

        val grosor = height * 0.20f
        trazo.strokeWidth = grosor
        val margen = grosor / 2f + 2f
        val lado = minOf(width, height).toFloat()
        val izquierda = (width - lado) / 2f
        marco.set(izquierda + margen, margen, izquierda + lado - margen, lado - margen)

        val total = porciones.sumOf { it.monto }
        if (total <= 0) {
            // Sin gastos se dibuja el anillo vacío, para que el hueco no parezca un error.
            trazo.color = 0x22000000
            lienzo.drawArc(marco, 0f, 360f, false, trazo)
            return
        }

        // Arranca arriba y avanza en el sentido del reloj, con una ranura entre porciones.
        var desde = -90f
        val ranura = if (porciones.size > 1) 3f else 0f
        porciones.forEach { porcion ->
            val barrido = (porcion.monto / total * 360f).toFloat() - ranura
            if (barrido > 0) {
                trazo.color = porcion.color
                lienzo.drawArc(marco, desde, barrido, false, trazo)
            }
            desde += barrido + ranura
        }
    }
}
