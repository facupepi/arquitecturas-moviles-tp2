package ar.edu.utn.frsfco.finanzas

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import ar.edu.utn.frsfco.finanzas.databinding.ActivityCategoriasBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaCategoriaBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration

/**
 * Pantalla para administrar las categorías propias.
 *
 * El usuario puede crear las suyas, cambiarles el nombre, el color y el ícono, o
 * borrarlas. Las cinco iniciales se pueden tocar como cualquier otra.
 */
class CategoriasActivity : PantallaBase() {

    private lateinit var binding: ActivityCategoriasBinding
    private val repositorio = CategoriasRepositorio()
    private var escucha: ListenerRegistration? = null
    private var categorias = listOf<Categoria>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepararNavegacion(binding.navegacion.barraNavegacion, R.id.navCategorias)
        binding.btnNueva.setOnClickListener {
            CategoriaSheet.abrir(null, categorias.size).show(supportFragmentManager, "categoria")
        }

        // La hoja avisa por acá y no por una función suelta: así el aviso llega
        // igual cuando la pantalla se rehizo mientras la hoja estaba abierta.
        supportFragmentManager.setFragmentResultListener(
            CategoriaSheet.PEDIDO, this
        ) { _, datos -> avisar(datos.getInt(HojaBase.MENSAJE)) }
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
            fila.root.setOnClickListener {
                CategoriaSheet.abrir(cat, categorias.size)
                    .show(supportFragmentManager, "categoria")
            }
            fila.btnBorrar.setOnClickListener { confirmarBorrado(cat) }
            binding.lista.addView(fila.root)
        }
    }

    private fun confirmarBorrado(categoria: Categoria) {
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(categoria.nombre)
            .setMessage(R.string.borrar_categoria_aviso)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.borrar) { _, _ ->
                repositorio.borrar(categoria.id)
                avisar(R.string.categoria_borrada)
            }
            .show()
        // Borrar es la acción destructiva: en rojo se distingue de cancelar.
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(this, R.color.rojo))
    }

    private fun avisar(mensaje: Int) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT).show()
    }
}
