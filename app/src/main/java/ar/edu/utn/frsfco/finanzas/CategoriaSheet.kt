package ar.edu.utn.frsfco.finanzas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import ar.edu.utn.frsfco.finanzas.databinding.HojaCategoriaBinding

/**
 * Hoja para crear una categoría o corregir una existente.
 *
 * El color y el ícono elegidos viven en el estado de la hoja porque no son texto de
 * ningún campo: si estuvieran sólo en las fichas, un giro de pantalla los perdería.
 */
class CategoriaSheet : HojaBase() {

    private var _binding: HojaCategoriaBinding? = null
    private val binding get() = _binding!!
    private val repositorio = CategoriasRepositorio()

    private var categoria: Categoria? = null
    private var orden: Int = 0
    private lateinit var color: String
    private lateinit var icono: String

    companion object {
        const val PEDIDO = "categoria"

        private const val ARG_CATEGORIA = "categoria"
        private const val ARG_ORDEN = "orden"
        private const val ESTADO_COLOR = "color"
        private const val ESTADO_ICONO = "icono"

        /** [orden] es el lugar que ocupará una categoría nueva dentro de la lista. */
        fun abrir(categoria: Categoria?, orden: Int) = CategoriaSheet().apply {
            arguments = bundleOf(ARG_CATEGORIA to categoria, ARG_ORDEN to orden)
        }
    }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        categoria = argumento(ARG_CATEGORIA, Categoria::class.java)
        orden = requireArguments().getInt(ARG_ORDEN)
        color = estado?.getString(ESTADO_COLOR) ?: categoria?.color ?: Iconos.colores.first()
        icono = estado?.getString(ESTADO_ICONO)
            ?: categoria?.icono
            ?: Iconos.disponibles.first().first
    }

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _binding = HojaCategoriaBinding.inflate(inflador, contenedor, false)
        return binding.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        binding.tvTitulo.setText(
            if (categoria == null) R.string.nueva_categoria else R.string.editar_categoria
        )
        if (estado == null) binding.etNombre.setText(categoria?.nombre.orEmpty())

        Fichas.armarColores(binding.grupoColores, { color }) { color = it }
        Fichas.armarIconos(binding.grupoIconos, { icono }) { icono = it }
        binding.btnGuardar.setOnClickListener { guardar() }
    }

    private fun guardar() {
        val nombre = binding.etNombre.text.toString().trim()
        if (nombre.isBlank()) {
            binding.etNombre.error = getString(R.string.nombre_vacio)
            return
        }
        repositorio.guardar(
            Categoria(
                id = categoria?.id.orEmpty(),
                nombre = nombre,
                color = color,
                icono = icono,
                orden = categoria?.orden ?: orden
            )
        )
        terminar(PEDIDO, bundleOf(MENSAJE to R.string.categoria_guardada))
    }

    override fun onSaveInstanceState(estado: Bundle) {
        super.onSaveInstanceState(estado)
        estado.putString(ESTADO_COLOR, color)
        estado.putString(ESTADO_ICONO, icono)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
