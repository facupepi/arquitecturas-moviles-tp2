package ar.edu.utn.frsfco.finanzas

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Pregunta de confirmación para lo que no se puede deshacer solo.
 *
 * Es un fragmento y no un diálogo armado a mano porque el sistema lo rehace al girar
 * la pantalla: creado a mano dejaba una ventana huérfana, que el registro del
 * dispositivo anotaba como pérdida, y la pregunta desaparecía sin respuesta.
 *
 * Al aceptar devuelve por el canal de resultados los mismos datos con los que se
 * abrió, así quien la mostró sabe sobre qué elemento tiene que actuar.
 */
class Confirmacion : DialogFragment() {

    companion object {
        private const val ARG_PEDIDO = "pedido"
        private const val ARG_TITULO = "titulo"
        private const val ARG_AVISO = "aviso"
        private const val ARG_ACCION = "accion"
        private const val ARG_DATOS = "datos"

        /**
         * [pedido] es el canal por el que llega la respuesta y [accion] el texto del
         * botón que confirma. En [datos] viaja lo que haga falta para ejecutarla.
         */
        fun pedir(
            pedido: String,
            titulo: String,
            aviso: Int,
            accion: Int,
            datos: Bundle = Bundle()
        ) = Confirmacion().apply {
            arguments = bundleOf(
                ARG_PEDIDO to pedido,
                ARG_TITULO to titulo,
                ARG_AVISO to aviso,
                ARG_ACCION to accion,
                ARG_DATOS to datos
            )
        }
    }

    override fun onCreateDialog(estado: Bundle?): Dialog {
        val datos = requireArguments()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(datos.getString(ARG_TITULO))
            .setMessage(datos.getInt(ARG_AVISO))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(datos.getInt(ARG_ACCION)) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    datos.getString(ARG_PEDIDO).orEmpty(),
                    datos.getBundle(ARG_DATOS) ?: Bundle()
                )
            }
            .create()
    }

    override fun onStart() {
        super.onStart()
        // La acción que confirma es destructiva: en rojo se distingue de cancelar.
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.rojo))
    }
}
