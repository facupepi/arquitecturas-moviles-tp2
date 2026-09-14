package ar.edu.utn.frsfco.finanzas

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.os.BundleCompat
import androidx.recyclerview.widget.LinearLayoutManager
import ar.edu.utn.frsfco.finanzas.databinding.ActivityPrincipalBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaDesgloseBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaLeyendaBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Resumen de gastos.
 *
 * Muestra cuánto se lleva gastado en el tramo de tiempo elegido, en qué se fue y los
 * últimos movimientos. Todo sale de Firestore y se actualiza solo cuando los datos
 * cambian. Si hay ingresos cargados, la cifra grande pasa a ser lo que queda.
 */
class PrincipalActivity : PantallaBase() {

    /** Tramos que se pueden mirar. El histórico completo es `TODO`. */
    enum class Periodo { ESTE_MES, MES_PASADO, ESTE_ANIO, TODO }

    private lateinit var binding: ActivityPrincipalBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val gastosRepo = GastosRepositorio()
    private val ingresosRepo = IngresosRepositorio()
    private val categoriasRepo = CategoriasRepositorio()
    private val mediosRepo = MediosRepositorio()
    private val fijosRepo = FijosRepositorio()

    private lateinit var adaptador: GastosAdapter
    private val escuchas = mutableListOf<ListenerRegistration>()

    private val gastos = mutableListOf<Gasto>()
    private val ingresos = mutableListOf<Ingreso>()
    private var categorias = listOf<Categoria>()
    private var medios = listOf<Medio>()
    private var fijos = listOf<GastoFijo>()

    private var periodo = Periodo.ESTE_MES
    private var filtroCategoria: String? = null

    private companion object {
        /** Cuántos gastos se listan acá; el resto se ve en el historial. */
        const val MAXIMO_LISTA = 10

        const val ESTADO_PERIODO = "periodo"
        const val ESTADO_FILTRO = "filtro"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // El tramo elegido y el filtro por categoría no viven en ninguna vista, así
        // que al girar la pantalla se perdían y el resumen volvía al mes en curso.
        savedInstanceState?.let { estado ->
            periodo = Periodo.valueOf(estado.getString(ESTADO_PERIODO, Periodo.ESTE_MES.name))
            filtroCategoria = estado.getString(ESTADO_FILTRO)
        }

        mostrarCuenta()

        adaptador = GastosAdapter { gasto -> abrirEdicion(gasto) }
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = adaptador

        prepararNavegacion(binding.navegacion.barraNavegacion, R.id.navResumen)
        encogerBotonAlBajar()
        binding.btnAgregar.setOnClickListener { abrirCarga() }
        binding.btnVerTodos.setOnClickListener { abrirHistorial() }
        binding.btnQuitarFiltro.setOnClickListener {
            filtroCategoria = null
            refrescar()
        }

        binding.grupoPeriodo.setOnCheckedStateChangeListener { _, elegidos ->
            periodo = when (elegidos.firstOrNull()) {
                R.id.chipMesPasado -> Periodo.MES_PASADO
                R.id.chipEsteAnio -> Periodo.ESTE_ANIO
                R.id.chipTodo -> Periodo.TODO
                else -> Periodo.ESTE_MES
            }
            refrescar()
        }

        escucharHoja()

        // La primera vez que entra el usuario se crean las categorías y los medios
        // de pago de arranque, para que pueda registrar un gasto sin configurar nada.
        categoriasRepo.sembrarSiHaceFalta()
        mediosRepo.sembrarSiHaceFalta()
    }

    /**
     * Queda a la espera de lo que decida la hoja de gastos.
     *
     * Pasa por el canal de resultados y no por una función suelta para que el aviso
     * llegue igual cuando la pantalla se rehizo mientras la hoja estaba abierta.
     */
    private fun escucharHoja() {
        supportFragmentManager.setFragmentResultListener(
            GastoSheet.PEDIDO, this
        ) { _, datos ->
            val borrado = BundleCompat.getSerializable(datos, GastoSheet.BORRADO, Gasto::class.java)
            if (borrado != null) avisarConDeshacer(borrado) else avisar(datos.getInt(HojaBase.MENSAJE))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ESTADO_PERIODO, periodo.name)
        outState.putString(ESTADO_FILTRO, filtroCategoria)
    }

    override fun onStart() {
        super.onStart()
        escuchas += categoriasRepo.escuchar { recibidas ->
            categorias = recibidas
            refrescar()
        }
        escuchas += gastosRepo.escuchar { recibidos ->
            gastos.clear()
            gastos.addAll(recibidos)
            crearFijosQueFaltan()
            refrescar()
        }
        escuchas += ingresosRepo.escuchar { recibidos ->
            ingresos.clear()
            ingresos.addAll(recibidos)
            refrescar()
        }
        escuchas += mediosRepo.escuchar { recibidos ->
            medios = recibidos
            refrescar()
        }
        escuchas += fijosRepo.escuchar { recibidos ->
            fijos = recibidos
            crearFijosQueFaltan()
        }
    }

    override fun onStop() {
        super.onStop()
        // Sin esto las escuchas seguirían consumiendo datos con la pantalla cerrada.
        escuchas.forEach { it.remove() }
        escuchas.clear()
    }

    /**
     * Mientras se recorre la lista, el botón se achica y deja de tapar el contenido;
     * al volver arriba recupera el texto. Extendido todo el tiempo se comía la
     * leyenda del gráfico.
     */
    private fun encogerBotonAlBajar() {
        binding.desplazable.setOnScrollChangeListener { _, _, y, _, anterior ->
            if (y > anterior && y > 40) binding.btnAgregar.shrink()
            if (y < anterior) binding.btnAgregar.extend()
        }
    }

    /** El alquiler y las expensas se cargan solos cuando llega su día. */
    private fun crearFijosQueFaltan() {
        if (fijos.isEmpty()) return
        FijosRepositorio.materializar(fijos, gastos, gastosRepo)
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
        val delTramo = gastos.filter { Tramos.entra(it.fecha, periodo) }
        val porId = categorias.associateBy { it.id }
        val total = delTramo.sumOf { it.monto }
        val entrado = ingresos.filter { Tramos.entra(it.fecha, periodo) }.sumOf { it.monto }

        mostrarCifra(total, entrado, delTramo.size)
        mostrarDesglose(total, entrado)

        val visibles = filtroCategoria?.let { id -> delTramo.filter { it.categoriaId == id } }
            ?: delTramo
        mostrarLista(visibles, porId, medios.associateBy { it.id })

        val hay = delTramo.isNotEmpty()
        binding.vacio.visibility = if (hay) View.GONE else View.VISIBLE
        binding.tarjetaLista.visibility = if (hay) View.VISIBLE else View.GONE
        binding.tituloLista.visibility = if (hay) View.VISIBLE else View.GONE
        binding.bloqueGrafico.visibility = if (hay) View.VISIBLE else View.GONE

        if (hay) dibujarGrafico(delTramo, porId, total) else explicarVacio()
    }

    /**
     * Con ingresos cargados el número grande es el saldo; sin ellos, lo gastado.
     * Preguntar cuánto queda fue lo primero que hicieron todos los que la probaron,
     * y la aplicación sólo sabía contestar cuánto se había ido.
     */
    private fun mostrarCifra(total: Double, entrado: Double, cuantos: Int) {
        val hayIngresos = entrado > 0
        binding.tvRotulo.setText(
            if (hayIngresos) R.string.saldo_disponible else Tramos.rotulo(periodo)
        )
        binding.tvTotal.text = Plata.formatear(if (hayIngresos) entrado - total else total)
        binding.tvCantidad.text =
            resources.getQuantityString(R.plurals.cantidad_gastos, cuantos, cuantos)
    }

    /**
     * El desglose de la tarjeta, una línea por concepto.
     *
     * Antes era un solo renglón con los importes separados por puntos medios y costaba
     * leerlo de un vistazo; ahora cada dato tiene su ícono y su renglón. El reparto por
     * medio de pago se ve en el historial, donde cada movimiento dice con qué se pagó.
     */
    private fun mostrarDesglose(total: Double, entrado: Double) {
        binding.bloqueDesglose.visibility = if (entrado > 0) View.VISIBLE else View.GONE
        if (entrado <= 0) return

        llenarFila(binding.filaIngresos, R.drawable.ic_entra, R.string.rotulo_ingresos, entrado)
        llenarFila(binding.filaGastos, R.drawable.ic_sale, R.string.rotulo_gastos, total)
    }

    private fun llenarFila(fila: FilaDesgloseBinding, icono: Int, concepto: Int, monto: Double) {
        fila.icono.setImageResource(icono)
        fila.tvConcepto.setText(concepto)
        fila.tvMonto.text = Plata.formatear(monto)
    }

    private fun mostrarLista(
        visibles: List<Gasto>,
        porId: Map<String, Categoria>,
        mediosPorId: Map<String, Medio>
    ) {
        adaptador.mostrar(
            visibles.take(MAXIMO_LISTA), porId, mediosPorId, periodo == Periodo.ESTE_MES
        )

        val filtrada = filtroCategoria?.let { porId[it] }
        binding.tvTituloLista.text = filtrada?.nombre?.uppercase()
            ?: getString(R.string.ultimos_gastos)
        binding.btnQuitarFiltro.visibility = if (filtrada != null) View.VISIBLE else View.GONE

        val hayMas = visibles.size > MAXIMO_LISTA
        binding.btnVerTodos.visibility = if (hayMas) View.VISIBLE else View.GONE
        if (hayMas) {
            binding.btnVerTodos.text =
                resources.getQuantityString(R.plurals.ver_todos, visibles.size, visibles.size)
        }
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

            // Tocar una categoría deja en la lista sólo sus gastos.
            if (cat.id.isNotBlank()) {
                fila.root.setOnClickListener {
                    filtroCategoria = if (filtroCategoria == cat.id) null else cat.id
                    refrescar()
                }
            }
            if (cat.id == filtroCategoria) {
                fila.root.backgroundTintList = ColorStateList.valueOf(cat.colorSuave())
            }
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

    private fun abrirCarga() {
        if (medios.isEmpty()) {
            avisar(R.string.sin_medios_aviso)
            return
        }
        GastoSheet.nuevo(categorias, medios).show(supportFragmentManager, GastoSheet.PEDIDO)
    }

    private fun abrirEdicion(gasto: Gasto) {
        GastoSheet.editar(gasto, categorias, medios).show(supportFragmentManager, GastoSheet.PEDIDO)
    }

    private fun abrirHistorial() {
        startActivity(
            Intent(this, HistorialActivity::class.java)
                .putExtra(HistorialActivity.EXTRA_PERIODO, periodo.name)
                .putExtra(HistorialActivity.EXTRA_CATEGORIA, filtroCategoria)
        )
    }

    private fun avisar(mensaje: Int) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.btnAgregar)
            .show()
    }

    /** Borrar no se puede deshacer desde el servidor, así que se repone el documento. */
    private fun avisarConDeshacer(borrado: Gasto) {
        Snackbar.make(binding.root, R.string.gasto_borrado, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.btnAgregar)
            .setAction(R.string.deshacer) { gastosRepo.restaurar(borrado) }
            .setActionTextColor(ContextCompat.getColor(this, R.color.verde_claro))
            .show()
    }
}

/** Decide qué entra en cada tramo de tiempo y cómo se llama. */
object Tramos {

    fun entra(fecha: Date, periodo: PrincipalActivity.Periodo): Boolean {
        val cuando = Calendar.getInstance().apply { time = fecha }
        val hoy = Calendar.getInstance()
        return when (periodo) {
            PrincipalActivity.Periodo.ESTE_MES -> mismoMes(cuando, hoy)
            PrincipalActivity.Periodo.MES_PASADO -> mismoMes(
                cuando, Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            )
            PrincipalActivity.Periodo.ESTE_ANIO ->
                cuando.get(Calendar.YEAR) == hoy.get(Calendar.YEAR)
            PrincipalActivity.Periodo.TODO -> true
        }
    }

    fun rotulo(periodo: PrincipalActivity.Periodo): Int = when (periodo) {
        PrincipalActivity.Periodo.ESTE_MES -> R.string.gastado_este_mes
        PrincipalActivity.Periodo.MES_PASADO -> R.string.gastado_mes_pasado
        PrincipalActivity.Periodo.ESTE_ANIO -> R.string.gastado_este_anio
        PrincipalActivity.Periodo.TODO -> R.string.gastado_total
    }

    private fun mismoMes(una: Calendar, otra: Calendar) =
        una.get(Calendar.YEAR) == otra.get(Calendar.YEAR) &&
            una.get(Calendar.MONTH) == otra.get(Calendar.MONTH)
}
