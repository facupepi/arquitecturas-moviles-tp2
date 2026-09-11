package ar.edu.utn.frsfco.finanzas

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ar.edu.utn.frsfco.finanzas.databinding.ItemGastoBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Muestra la lista de gastos, uno por fila.
 *
 * Recibe las categorías aparte porque ahora las define el usuario, así que el color
 * y el ícono de cada fila se resuelven al momento de dibujarla.
 */
class GastosAdapter(
    private val alTocar: (Gasto) -> Unit
) : RecyclerView.Adapter<GastosAdapter.Fila>() {

    private val gastos = mutableListOf<Gasto>()
    private var categorias = mapOf<String, Categoria>()
    private val formatoFecha = SimpleDateFormat("d 'de' MMMM", Locale("es", "AR"))

    fun mostrar(nuevos: List<Gasto>, porId: Map<String, Categoria>) {
        gastos.clear()
        gastos.addAll(nuevos)
        categorias = porId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(padre: ViewGroup, tipo: Int): Fila {
        val binding = ItemGastoBinding.inflate(LayoutInflater.from(padre.context), padre, false)
        return Fila(binding)
    }

    override fun onBindViewHolder(fila: Fila, posicion: Int) = fila.mostrar(gastos[posicion])

    override fun getItemCount() = gastos.size

    inner class Fila(private val binding: ItemGastoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun mostrar(gasto: Gasto) {
            // Si la categoría fue borrada, el gasto se sigue viendo con una de reserva.
            val categoria = categorias[gasto.categoriaId] ?: Categoria.desconocida()

            binding.icono.setImageResource(categoria.iconoDibujable())
            binding.icono.imageTintList = ColorStateList.valueOf(categoria.colorEntero())
            binding.fondoIcono.setCardBackgroundColor(categoria.colorSuave())

            binding.tvCategoria.text = categoria.nombre
            val cuando = cuandoFue(gasto.fecha)
            binding.tvDetalle.text =
                if (gasto.detalle.isBlank()) cuando else "$cuando · ${gasto.detalle}"
            binding.tvMonto.text = Plata.formatear(gasto.monto)

            binding.root.setOnClickListener { alTocar(gasto) }
        }

        /** Hoy y ayer se nombran así; el resto lleva la fecha. */
        private fun cuandoFue(fecha: Date): String = when (diasDesde(fecha)) {
            0 -> binding.root.context.getString(R.string.hoy)
            1 -> binding.root.context.getString(R.string.ayer)
            else -> formatoFecha.format(fecha)
        }

        private fun diasDesde(fecha: Date): Int {
            fun aMedianoche(d: Date) = Calendar.getInstance().apply {
                time = d
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val unDia = 24 * 60 * 60 * 1000L
            val diferencia = aMedianoche(Date()).timeInMillis - aMedianoche(fecha).timeInMillis
            return (diferencia / unDia).toInt()
        }
    }
}
