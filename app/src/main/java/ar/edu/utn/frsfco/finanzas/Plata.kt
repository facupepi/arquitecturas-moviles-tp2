package ar.edu.utn.frsfco.finanzas

import java.text.NumberFormat
import java.util.Locale

/** Da formato a los importes con el separador de miles argentino. */
object Plata {

    private val formato = NumberFormat.getNumberInstance(Locale("es", "AR")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    /** Devuelve el importe con el signo adelante, por ejemplo $12.500. */
    fun formatear(monto: Double): String = "$" + formato.format(monto)
}
