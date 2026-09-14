package ar.edu.utn.frsfco.finanzas

import android.content.Context
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Elegir y mostrar la fecha de un movimiento.
 *
 * Casi nadie anota en el momento: la mayoría carga a la noche o el fin de semana, y
 * sin poder corregir el día los totales del mes quedaban mal. Lo usan la hoja de
 * gastos y la de ingresos, así que vive acá y no dentro de ninguna de las dos.
 */
object Fechas {

    private const val ETIQUETA = "calendario"

    private val conDia = SimpleDateFormat("EEEE d 'de' MMMM", Idioma.español)

    /** Hoy y ayer se nombran así; el resto lleva el día de la semana y la fecha. */
    fun nombrar(contexto: Context, fecha: Date): String {
        val cuando = Calendar.getInstance().apply { time = fecha }
        val hoy = Calendar.getInstance()
        val ayer = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }

        return when {
            mismoDia(cuando, hoy) -> contexto.getString(R.string.hoy)
            mismoDia(cuando, ayer) -> contexto.getString(R.string.ayer)
            else -> conDia.format(fecha).replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Abre el calendario del sistema. No deja elegir días futuros porque un
     * movimiento que todavía no ocurrió no tiene por qué sumar al total del mes.
     */
    fun elegir(hoja: Fragment, actual: Date, alElegir: (Date) -> Unit) {
        val limite = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val calendario = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.cuando_fue)
            .setSelection(aUtc(actual))
            .setCalendarConstraints(limite)
            .build()

        calendario.addOnPositiveButtonClickListener { elegido -> alElegir(desdeUtc(elegido)) }
        calendario.show(hoja.childFragmentManager, ETIQUETA)
    }

    /**
     * Vuelve a atender el calendario que haya quedado abierto.
     *
     * Al girar la pantalla el sistema rehace el calendario pero no sus escuchas, que
     * no forman parte del estado guardado. Sin esto, después de un giro se podía
     * elegir un día y aceptar sin que la fecha del movimiento cambiara.
     */
    fun reconectar(hoja: Fragment, alElegir: (Date) -> Unit) {
        val abierto = hoja.childFragmentManager.findFragmentByTag(ETIQUETA)
        @Suppress("UNCHECKED_CAST")
        (abierto as? MaterialDatePicker<Long>)
            ?.addOnPositiveButtonClickListener { elegido -> alElegir(desdeUtc(elegido)) }
    }

    private fun mismoDia(una: Calendar, otra: Calendar) =
        una.get(Calendar.YEAR) == otra.get(Calendar.YEAR) &&
            una.get(Calendar.DAY_OF_YEAR) == otra.get(Calendar.DAY_OF_YEAR)

    /**
     * El calendario trabaja en horario universal y devuelve la medianoche de ese día.
     * Sin esta conversión, en Argentina el día elegido se corría para atrás.
     */
    private fun aUtc(cuando: Date): Long {
        val local = Calendar.getInstance().apply { time = cuando }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
    }

    /** Conserva la hora actual: así los movimientos del mismo día quedan en orden. */
    private fun desdeUtc(milisegundos: Long): Date {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = milisegundos
        }
        val ahora = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(
                utc.get(Calendar.YEAR),
                utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH),
                ahora.get(Calendar.HOUR_OF_DAY),
                ahora.get(Calendar.MINUTE)
            )
        }.time
    }
}
