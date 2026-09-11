package ar.edu.utn.frsfco.finanzas

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import ar.edu.utn.frsfco.finanzas.databinding.HojaGastoBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Hoja para cargar un gasto, o para corregir uno ya cargado.
 *
 * Aparece sobre el resumen en lugar de abrir otra pantalla, para que anotar un gasto
 * cueste lo menos posible. Cuando recibe un gasto existente pasa a modo edición y
 * suma el botón de borrar.
 */
class GastoSheet : BottomSheetDialogFragment() {

    private var _binding: HojaGastoBinding? = null
    private val binding get() = _binding!!
    private val repositorio = GastosRepositorio()

    private var categorias: List<Categoria> = emptyList()
    private var elegida: Categoria? = null
    private var gasto: Gasto? = null

    /** Avisa al resumen qué pasó, para mostrar el mensaje correspondiente. */
    var alTerminar: ((String) -> Unit)? = null

    companion object {
        fun nuevo(categorias: List<Categoria>) = GastoSheet().apply {
            this.categorias = categorias
        }

        fun editar(gasto: Gasto, categorias: List<Categoria>) = GastoSheet().apply {
            this.categorias = categorias
            this.gasto = gasto
        }
    }

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _binding = HojaGastoBinding.inflate(inflador, contenedor, false)
        return binding.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        elegida = gasto?.let { g -> categorias.firstOrNull { it.id == g.categoriaId } }
            ?: categorias.firstOrNull()

        armarCategorias()
        completarSiEsEdicion()

        binding.btnGuardar.setOnClickListener { guardar() }
        binding.btnBorrar.setOnClickListener { confirmarBorrado() }

        // En un gasto nuevo lo primero es el monto, así que el teclado sube solo.
        // Al editar no: el teclado tapaba el botón de borrar y las categorías.
        // ADJUST_RESIZE figura como obsoleto desde API 30, pero en una hoja como
        // esta sigue siendo la forma directa de que el teclado empuje el contenido.
        val esNuevo = gasto == null
        if (esNuevo) binding.etMonto.requestFocus()
        dialog?.window?.setSoftInputMode(
            (if (esNuevo) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            else WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        // Sin esto la hoja se queda a media altura y el teclado tapa el botón de guardar.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun completarSiEsEdicion() {
        val g = gasto ?: return
        binding.tvTitulo.setText(R.string.editar_gasto)
        binding.etMonto.setText(g.monto.toLong().toString())
        binding.etMonto.setSelection(binding.etMonto.text?.length ?: 0)
        binding.etDetalle.setText(g.detalle)
        binding.btnBorrar.visibility = View.VISIBLE
    }

    /**
     * Arma una ficha por categoría.
     *
     * El color de la ficha cambia según esté elegida o no. Antes el fondo era fijo y
     * por eso no se distinguía cuál estaba seleccionada.
     */
    private fun armarCategorias() {
        binding.grupoCategorias.removeAllViews()
        categorias.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat.nombre
                isCheckable = true
                isChecked = cat.id == elegida?.id
                chipIcon = ContextCompat.getDrawable(requireContext(), cat.iconoDibujable())
                chipIconTint = ColorStateList.valueOf(cat.colorEntero())
                typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_medium)
                setOnClickListener {
                    elegida = cat
                    repintarTodas()
                }
            }
            pintar(chip, cat, cat.id == elegida?.id)
            binding.grupoCategorias.addView(chip)
        }
    }

    /** Las fichas se agregaron en el orden de la lista, así que van una a una. */
    private fun repintarTodas() {
        categorias.forEachIndexed { posicion, cat ->
            val chip = binding.grupoCategorias.getChildAt(posicion) as Chip
            val esta = cat.id == elegida?.id
            chip.isChecked = esta
            pintar(chip, cat, esta)
        }
    }

    /** La elegida se llena con su color; el resto queda en blanco con borde gris. */
    private fun pintar(chip: Chip, cat: Categoria, esta: Boolean) {
        val contexto = requireContext()
        chip.chipBackgroundColor = ColorStateList.valueOf(
            if (esta) cat.colorSuave() else ContextCompat.getColor(contexto, R.color.superficie)
        )
        chip.chipStrokeWidth = if (esta) 4f else 2f
        chip.chipStrokeColor = ColorStateList.valueOf(
            if (esta) cat.colorEntero() else ContextCompat.getColor(contexto, R.color.borde)
        )
        chip.setTextColor(
            if (esta) cat.colorEntero() else ContextCompat.getColor(contexto, R.color.texto)
        )
    }

    private fun guardar() {
        val monto = binding.etMonto.text.toString().toDoubleOrNull()
        if (monto == null || monto <= 0) {
            binding.etMonto.error = getString(R.string.monto_invalido)
            return
        }
        val categoria = elegida
        if (categoria == null) {
            Toast.makeText(requireContext(), R.string.sin_categorias, Toast.LENGTH_LONG).show()
            return
        }

        mostrarEspera(true)
        lifecycleScope.launch {
            try {
                val detalle = binding.etDetalle.text.toString()
                val existente = gasto
                if (existente == null) {
                    repositorio.agregar(monto, categoria.id, detalle)
                } else {
                    repositorio.actualizar(existente.id, monto, categoria.id, detalle, existente.fecha)
                }
                ocultarTeclado()
                alTerminar?.invoke(
                    getString(if (existente == null) R.string.gasto_guardado else R.string.gasto_actualizado)
                )
                dismiss()
            } catch (e: Exception) {
                mostrarEspera(false)
                Toast.makeText(requireContext(), R.string.error_guardar, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmarBorrado() {
        val existente = gasto ?: return
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.borrar_gasto)
            .setMessage(R.string.borrar_gasto_aviso)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.borrar) { _, _ -> borrar(existente.id) }
            .show()
        // Borrar es la acción destructiva: en rojo se distingue de cancelar.
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.rojo))
    }

    private fun borrar(id: String) {
        mostrarEspera(true)
        lifecycleScope.launch {
            try {
                repositorio.borrar(id)
                ocultarTeclado()
                alTerminar?.invoke(getString(R.string.gasto_borrado))
                dismiss()
            } catch (e: Exception) {
                mostrarEspera(false)
                Toast.makeText(requireContext(), R.string.error_guardar, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun ocultarTeclado() {
        val servicio = requireContext().getSystemService(InputMethodManager::class.java)
        servicio?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun mostrarEspera(esperando: Boolean) {
        binding.progreso.visibility = if (esperando) View.VISIBLE else View.GONE
        binding.btnGuardar.isEnabled = !esperando
        binding.btnGuardar.text = if (esperando) "" else getString(R.string.btn_guardar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
