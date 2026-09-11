package ar.edu.utn.frsfco.finanzas

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ar.edu.utn.frsfco.finanzas.databinding.ActivityCategoriasBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaCategoriaBinding
import ar.edu.utn.frsfco.finanzas.databinding.HojaCategoriaBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * Pantalla para administrar las categorías propias.
 *
 * El usuario puede crear las suyas, cambiarles el nombre, el color y el ícono, o
 * borrarlas. Las cinco iniciales se pueden tocar como cualquier otra.
 */
class CategoriasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriasBinding
    private val repositorio = CategoriasRepositorio()
    private var escucha: ListenerRegistration? = null
    private var categorias = listOf<Categoria>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.barra.setNavigationOnClickListener { finish() }
        binding.btnNueva.setOnClickListener { abrirHoja(null) }
    }

    override fun onStart() {
        super.onStart()
        escucha = repositorio.escuchar { recibidas ->
            categorias = recibidas
            dibujarLista()
        }
    }

    override fun onStop() {
        super.onStop()
        escucha?.remove()
        escucha = null
    }

    private fun dibujarLista() {
        binding.lista.removeAllViews()
        binding.vacio.visibility = if (categorias.isEmpty()) View.VISIBLE else View.GONE

        categorias.forEach { cat ->
            val fila = FilaCategoriaBinding.inflate(LayoutInflater.from(this), binding.lista, false)
            fila.icono.setImageResource(cat.iconoDibujable())
            fila.icono.imageTintList = ColorStateList.valueOf(cat.colorEntero())
            fila.fondoIcono.setCardBackgroundColor(cat.colorSuave())
            fila.tvNombre.text = cat.nombre
            fila.root.setOnClickListener { abrirHoja(cat) }
            fila.btnBorrar.setOnClickListener { confirmarBorrado(cat) }
            binding.lista.addView(fila.root)
        }
    }

    /**
     * Hoja para crear una categoría nueva o corregir una existente. Recibe null
     * cuando es nueva.
     *
     * El color y el ícono elegidos viven acá mientras la hoja está abierta; las
     * fichas sólo avisan cuál se tocó.
     */
    private fun abrirHoja(categoria: Categoria?) {
        val hoja = BottomSheetDialog(this)
        val vista = HojaCategoriaBinding.inflate(layoutInflater)
        hoja.setContentView(vista.root)

        var color = categoria?.color ?: Iconos.colores.first()
        var icono = categoria?.icono ?: Iconos.disponibles.first().first

        vista.tvTitulo.setText(
            if (categoria == null) R.string.nueva_categoria else R.string.editar_categoria
        )
        vista.etNombre.setText(categoria?.nombre.orEmpty())

        armarColores(vista.grupoColores, color) { color = it }
        armarIconos(vista.grupoIconos, icono) { icono = it }

        vista.btnGuardar.setOnClickListener {
            val nombre = vista.etNombre.text.toString().trim()
            if (nombre.isBlank()) {
                vista.etNombre.error = getString(R.string.nombre_vacio)
                return@setOnClickListener
            }
            guardar(
                Categoria(
                    id = categoria?.id.orEmpty(),
                    nombre = nombre,
                    color = color,
                    icono = icono,
                    // Las nuevas van al final de la lista.
                    orden = categoria?.orden ?: categorias.size
                ),
                hoja
            )
        }

        hoja.show()
    }

    /** Una ficha por color de la paleta. */
    private fun armarColores(grupo: ChipGroup, elegido: String, alElegir: (String) -> Unit) {
        Iconos.colores.forEach { hex ->
            val ficha = Chip(this)
            ficha.text = ""
            ficha.isCheckable = false
            ficha.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(hex))
            ficha.chipStrokeColor = ColorStateList.valueOf(color(R.color.texto))
            ficha.setOnClickListener {
                alElegir(hex)
                marcarColor(grupo, hex)
            }
            grupo.addView(ficha)
        }
        marcarColor(grupo, elegido)
    }

    /** Al color elegido se le pone un borde oscuro; es lo único que lo distingue. */
    private fun marcarColor(grupo: ChipGroup, elegido: String) {
        Iconos.colores.forEachIndexed { posicion, hex ->
            val ficha = grupo.getChildAt(posicion) as Chip
            ficha.chipStrokeWidth = if (hex == elegido) 6f else 0f
        }
    }

    /** Una ficha por ícono del catálogo. */
    private fun armarIconos(grupo: ChipGroup, elegido: String, alElegir: (String) -> Unit) {
        Iconos.disponibles.forEach { (clave, dibujable) ->
            val ficha = Chip(this)
            ficha.text = ""
            ficha.isCheckable = false
            ficha.chipIcon = ContextCompat.getDrawable(this, dibujable)
            ficha.chipIconTint = ColorStateList.valueOf(color(R.color.texto))
            ficha.chipBackgroundColor = ColorStateList.valueOf(color(R.color.superficie))
            ficha.setOnClickListener {
                alElegir(clave)
                marcarIcono(grupo, clave)
            }
            grupo.addView(ficha)
        }
        marcarIcono(grupo, elegido)
    }

    /** El ícono elegido se marca con un borde verde, más grueso que el de los demás. */
    private fun marcarIcono(grupo: ChipGroup, elegido: String) {
        Iconos.disponibles.forEachIndexed { posicion, (clave, _) ->
            val ficha = grupo.getChildAt(posicion) as Chip
            val esta = clave == elegido
            ficha.chipStrokeWidth = if (esta) 4f else 2f
            ficha.chipStrokeColor =
                ColorStateList.valueOf(color(if (esta) R.color.verde else R.color.borde))
        }
    }

    private fun guardar(categoria: Categoria, hoja: BottomSheetDialog) {
        lifecycleScope.launch {
            runCatching { repositorio.guardar(categoria) }
                .onSuccess {
                    hoja.dismiss()
                    avisar(getString(R.string.categoria_guardada))
                }
                .onFailure { avisar(getString(R.string.error_guardar)) }
        }
    }

    private fun confirmarBorrado(categoria: Categoria) {
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(categoria.nombre)
            .setMessage(R.string.borrar_categoria_aviso)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.borrar) { _, _ ->
                lifecycleScope.launch {
                    runCatching { repositorio.borrar(categoria.id) }
                        .onSuccess { avisar(getString(R.string.categoria_borrada)) }
                        .onFailure { avisar(getString(R.string.error_guardar)) }
                }
            }
            .show()
        // Borrar es la acción destructiva: en rojo se distingue de cancelar.
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color(R.color.rojo))
    }

    private fun color(@ColorRes recurso: Int) = ContextCompat.getColor(this, recurso)

    private fun avisar(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT).show()
    }
}
