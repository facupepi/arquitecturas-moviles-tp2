package ar.edu.utn.frsfco.finanzas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import ar.edu.utn.frsfco.finanzas.databinding.HojaMedioBinding

/**
 * Hoja para crear un medio de pago o corregir uno existente.
 *
 * Pide lo mismo que la de categorías, nombre, color e ícono, porque los medios se
 * dibujan igual en las fichas de carga y en la lista de ajustes.
 */
class MedioSheet : HojaBase() {

    private var _binding: HojaMedioBinding? = null
    private val binding get() = _binding!!
    private val repositorio = MediosRepositorio()

    private var medio: Medio? = null
    private var orden: Int = 0
    private lateinit var color: String
    private lateinit var icono: String

    companion object {
        const val PEDIDO = "medio"

        private const val ARG_MEDIO = "medio"
        private const val ARG_ORDEN = "orden"
        private const val ESTADO_COLOR = "color"
        private const val ESTADO_ICONO = "icono"

        /** [orden] es el lugar que ocupará un medio nuevo dentro de la lista. */
        fun abrir(medio: Medio?, orden: Int) = MedioSheet().apply {
            arguments = bundleOf(ARG_MEDIO to medio, ARG_ORDEN to orden)
        }
    }

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        medio = argumento(ARG_MEDIO, Medio::class.java)
        orden = requireArguments().getInt(ARG_ORDEN)
        color = estado?.getString(ESTADO_COLOR) ?: medio?.color ?: Iconos.colores.first()
        icono = estado?.getString(ESTADO_ICONO) ?: medio?.icono ?: "billetera"
    }

    override fun onCreateView(inflador: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
        _binding = HojaMedioBinding.inflate(inflador, contenedor, false)
        return binding.root
    }

    override fun onViewCreated(vista: View, estado: Bundle?) {
        super.onViewCreated(vista, estado)

        binding.tvTitulo.setText(
            if (medio == null) R.string.nuevo_medio else R.string.editar_medio
        )
        // El campo se completa una sola vez: después de un giro vuelve solo con lo
        // que el usuario haya escrito.
        if (estado == null) binding.etNombre.setText(medio?.nombre.orEmpty())

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
            Medio(
                id = medio?.id.orEmpty(),
                nombre = nombre,
                color = color,
                icono = icono,
                orden = medio?.orden ?: orden
            )
        )
        terminar(PEDIDO, bundleOf(MENSAJE to R.string.medio_guardado))
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
