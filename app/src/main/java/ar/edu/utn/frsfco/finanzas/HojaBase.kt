package ar.edu.utn.frsfco.finanzas

import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.Serializable

/**
 * Base de las hojas modales que se levantan desde abajo.
 *
 * Al girar la pantalla el sistema destruye la hoja y la vuelve a crear con su
 * constructor vacío, así que todo lo que estuviera en una propiedad se pierde: la hoja
 * de gastos volvía sin ninguna categoría para elegir y el gasto ya no se podía guardar.
 * Por eso los datos de apertura viajan en los argumentos, lo que el usuario va
 * eligiendo se guarda en el estado, y el aviso posterior se manda por el canal de
 * resultados del administrador de fragmentos, que sobrevive al giro igual que la hoja.
 */
abstract class HojaBase : BottomSheetDialogFragment() {

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)
        // Sin esto la hoja se queda a media altura y el teclado tapa el botón de guardar.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    /** Cierra la hoja avisando a la pantalla que la abrió qué terminó pasando. */
    protected fun terminar(pedido: String, datos: Bundle) {
        parentFragmentManager.setFragmentResult(pedido, datos)
        dismiss()
    }

    /** Lee un dato de apertura; nulo si la hoja se abrió sin él. */
    protected fun <T : Serializable> argumento(clave: String, tipo: Class<T>): T? =
        BundleCompat.getSerializable(requireArguments(), clave, tipo)

    /** Lee un dato del estado que quedó guardado antes del giro. */
    protected fun <T : Serializable> guardado(estado: Bundle?, clave: String, tipo: Class<T>): T? =
        estado?.let { BundleCompat.getSerializable(it, clave, tipo) }

    companion object {
        /** Clave del texto que la hoja pide mostrar al cerrarse. */
        const val MENSAJE = "mensaje"
    }
}
