package ar.edu.utn.frsfco.finanzas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ar.edu.utn.frsfco.finanzas.databinding.ActivityPrincipalBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaLeyendaBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Resumen de gastos.
 *
 * Muestra cuánto se lleva gastado en el tramo de tiempo elegido, en qué se fue y los
 * últimos movimientos. Todo sale de Firestore y se actualiza solo cuando los datos cambian.
 */
class PrincipalActivity : AppCompatActivity() {

    /** Tramos que se pueden mirar. El histórico completo es `TODO`. */
    private enum class Periodo { ESTE_MES, MES_PASADO, ESTE_ANIO, TODO }

    private lateinit var binding: ActivityPrincipalBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val gastosRepo = GastosRepositorio()
    private val categoriasRepo = CategoriasRepositorio()

    private lateinit var adaptador: GastosAdapter
    private var escuchaGastos: ListenerRegistration? = null
    private var escuchaCategorias: ListenerRegistration? = null

    private val gastos = mutableListOf<Gasto>()
    private var categorias = listOf<Categoria>()
    private var periodo = Periodo.ESTE_MES

    private companion object {
        /** Cuántos gastos se listan; el resto se resume en una línea al pie. */
        const val MAXIMO_LISTA = 20
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mostrarCuenta()

        adaptador = GastosAdapter { gasto -> abrirEdicion(gasto) }
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = adaptador

        binding.btnSalir.setOnClickListener { cerrarSesion() }
        binding.btnCategorias.setOnClickListener { abrirCategorias() }
        binding.btnAgregar.setOnClickListener { abrirCarga() }

        binding.grupoPeriodo.setOnCheckedStateChangeListener { _, elegidos ->
            periodo = when (elegidos.firstOrNull()) {
                R.id.chipMesPasado -> Periodo.MES_PASADO
                R.id.chipEsteAnio -> Periodo.ESTE_ANIO
                R.id.chipTodo -> Periodo.TODO
                else -> Periodo.ESTE_MES
            }
            refrescar()
        }

        // La primera vez que entra el usuario se crean las categorías de arranque.
        lifecycleScope.launch {
            runCatching { categoriasRepo.sembrarSiHaceFalta() }
        }
    }

    override fun onStart() {
        super.onStart()
        escuchaCategorias = categoriasRepo.escuchar { recibidas ->
            categorias = recibidas
            refrescar()
        }
        escuchaGastos = gastosRepo.escuchar { recibidos ->
            gastos.clear()
            gastos.addAll(recibidos)
            refrescar()
        }
    }

    override fun onStop() {
        super.onStop()
        // Sin esto las escuchas seguirían consumiendo datos con la pantalla cerrada.
        escuchaGastos?.remove()
        escuchaCategorias?.remove()
        escuchaGastos = null
        escuchaCategorias = null
    }

    private fun mostrarCuenta() {
        val usuario = auth.currentUser
        val nombre = usuario?.displayName?.substringBefore(' ')
        binding.tvNombre.text = if (nombre.isNullOrBlank()) {
            getString(R.string.titulo_resumen)
        } else {
            getString(R.string.saludo, nombre)
        }
        binding.tvCuenta.text = usuario?.email.orEmpty()
    }

    private fun refrescar() {
        val delTramo = gastos.filter { entraEnElTramo(it.fecha) }
        val porId = categorias.associateBy { it.id }
        val total = delTramo.sumOf { it.monto }

        binding.tvRotulo.setText(rotulo())
        binding.tvTotal.text = Plata.formatear(total)
        binding.tvCantidad.text = resources.getQuantityString(
            R.plurals.cantidad_gastos, delTramo.size, delTramo.size
        )

        // Con muchos meses a la vista la lista se vuelve larguísima, así que se recorta
        // y el pie aclara cuántos quedaron sin mostrar.
        adaptador.mostrar(delTramo.take(MAXIMO_LISTA), porId)
        val resto = delTramo.size - MAXIMO_LISTA
        binding.tvResto.visibility = if (resto > 0) View.VISIBLE else View.GONE
        if (resto > 0) {
            binding.tvResto.text = resources.getQuantityString(R.plurals.resto_gastos, resto, resto)
        }

        val hay = delTramo.isNotEmpty()
        binding.vacio.visibility = if (hay) View.GONE else View.VISIBLE
        binding.tarjetaLista.visibility = if (hay) View.VISIBLE else View.GONE
        binding.tituloLista.visibility = if (hay) View.VISIBLE else View.GONE
        binding.bloqueGrafico.visibility = if (hay) View.VISIBLE else View.GONE

        if (hay) dibujarGrafico(delTramo, porId, total) else explicarVacio()
    }

    /** Reparte el total del tramo entre las categorías y arma el anillo con su leyenda. */
    private fun dibujarGrafico(delTramo: List<Gasto>, porId: Map<String, Categoria>, total: Double) {
        val porCategoria = delTramo
            .groupBy { it.categoriaId }
            .map { (id, suyos) ->
                (porId[id] ?: Categoria.desconocida()) to suyos.sumOf { it.monto }
            }
            .sortedByDescending { it.second }

        binding.grafico.mostrar(
            porCategoria.map { (cat, monto) -> GraficoAnillo.Porcion(cat.colorEntero(), monto) }
        )

        // Van todas: si alguna quedara afuera, su porción del anillo no tendría explicación.
        binding.leyenda.removeAllViews()
        porCategoria.forEach { (cat, monto) ->
            val fila = FilaLeyendaBinding.inflate(LayoutInflater.from(this), binding.leyenda, false)
            val punto = fila.punto.background.mutate()
            DrawableCompat.setTint(punto, cat.colorEntero())
            fila.punto.background = punto
            fila.tvNombre.text = cat.nombre
            fila.tvPorcentaje.text = getString(
                R.string.porcentaje, (monto / total * 100).roundToInt()
            )
            fila.tvMonto.text = Plata.formatear(monto)
            binding.leyenda.addView(fila.root)
        }
    }

    /** El aviso cambia según sea la primera vez o un tramo sin movimientos. */
    private fun explicarVacio() {
        val recienEmpieza = gastos.isEmpty()
        binding.tvVacioTitulo.setText(
            if (recienEmpieza) R.string.chanchito_titulo else R.string.sin_periodo_titulo
        )
        binding.tvVacioBajada.setText(
            if (recienEmpieza) R.string.chanchito_bajada else R.string.sin_periodo_bajada
        )
    }

    private fun rotulo(): Int = when (periodo) {
        Periodo.ESTE_MES -> R.string.gastado_este_mes
        Periodo.MES_PASADO -> R.string.gastado_mes_pasado
        Periodo.ESTE_ANIO -> R.string.gastado_este_anio
        Periodo.TODO -> R.string.gastado_total
    }

    private fun entraEnElTramo(fecha: Date): Boolean {
        val cuando = Calendar.getInstance().apply { time = fecha }
        val hoy = Calendar.getInstance()
        return when (periodo) {
            Periodo.ESTE_MES -> mismoMes(cuando, hoy)
            Periodo.MES_PASADO -> mismoMes(
                cuando, Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            )
            Periodo.ESTE_ANIO -> cuando.get(Calendar.YEAR) == hoy.get(Calendar.YEAR)
            Periodo.TODO -> true
        }
    }

    private fun mismoMes(una: Calendar, otra: Calendar) =
        una.get(Calendar.YEAR) == otra.get(Calendar.YEAR) &&
            una.get(Calendar.MONTH) == otra.get(Calendar.MONTH)

    private fun abrirCarga() {
        val hoja = GastoSheet.nuevo(categorias)
        hoja.alTerminar = { mensaje -> avisar(mensaje) }
        hoja.show(supportFragmentManager, "gasto")
    }

    private fun abrirEdicion(gasto: Gasto) {
        val hoja = GastoSheet.editar(gasto, categorias)
        hoja.alTerminar = { mensaje -> avisar(mensaje) }
        hoja.show(supportFragmentManager, "gasto")
    }

    private fun abrirCategorias() {
        startActivity(Intent(this, CategoriasActivity::class.java))
    }

    private fun avisar(mensaje: String) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnAgregar)
            .show()
    }

    private fun cerrarSesion() {
        escuchaGastos?.remove()
        escuchaCategorias?.remove()
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
