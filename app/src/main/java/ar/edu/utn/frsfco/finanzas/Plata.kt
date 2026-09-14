package ar.edu.utn.frsfco.finanzas

import java.text.NumberFormat

/**
 * Da formato a los importes con el separador de miles argentino.
 *
 * Los centavos sólo se muestran cuando el importe los tiene. Con los precios de hoy
 * la mayoría de los gastos son redondos y llenar la pantalla de ",00" no aporta nada.
 */
object Plata {

    private val sinDecimales = NumberFormat.getNumberInstance(Idioma.español).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    private val conDecimales = NumberFormat.getNumberInstance(Idioma.español).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /** Devuelve el importe con el signo adelante, por ejemplo $12.500 o $1.250,50. */
    fun formatear(monto: Double): String = "$" + numero(monto)

    /** Lo mismo pero sin el signo, para cuando el peso ya está escrito al lado. */
    fun numero(monto: Double): String =
        if (tieneCentavos(monto)) conDecimales.format(monto) else sinDecimales.format(monto)

    /** Agrupa de a miles un número entero ya escrito, para el campo de carga. */
    fun agruparMiles(entero: String): String {
        val limpio = entero.trimStart('0').ifEmpty { return entero.take(1) }
        return sinDecimales.format(limpio.toLong())
    }

    /**
     * Interpreta lo que el usuario escribió en el campo de monto.
     *
     * Llega con los puntos de miles puestos y la coma como separador decimal, que es
     * al revés de lo que espera toDouble.
     */
    fun leer(texto: String): Double? =
        texto.replace(".", "").replace(",", ".").toDoubleOrNull()

    private fun tieneCentavos(monto: Double) = kotlin.math.abs(monto % 1.0) > 0.004
}
