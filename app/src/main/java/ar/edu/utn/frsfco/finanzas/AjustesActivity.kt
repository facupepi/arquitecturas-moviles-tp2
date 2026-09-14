package ar.edu.utn.frsfco.finanzas

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import ar.edu.utn.frsfco.finanzas.databinding.ActivityAjustesBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaFijoBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaIngresoBinding
import ar.edu.utn.frsfco.finanzas.databinding.FilaMedioBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

/**
 * Lo que no se toca todos los días.
 *
 * Junta el sueldo y demás ingresos, los gastos que se repiten solos y la copia de
 * los datos. También vive acá el cierre de sesión, que antes
 * estaba pegado al botón de categorías y se tocaba sin querer.
 */
class AjustesActivity : PantallaBase() {

    private lateinit var binding: ActivityAjustesBinding
    private val fijosRepo = FijosRepositorio()
    private val ingresosRepo = IngresosRepositorio()
    private val categoriasRepo = CategoriasRepositorio()
    private val mediosRepo = MediosRepositorio()
    private val gastosRepo = GastosRepositorio()
    private val acceso by lazy { AccesoGoogle(this) }
    private val escuchas = mutableListOf<ListenerRegistration>()

    private val formatoFecha = SimpleDateFormat("d 'de' MMMM", Idioma.español)

    private var fijos = listOf<GastoFijo>()
    private var categorias = listOf<Categoria>()
    private var gastos = listOf<Gasto>()
    private var medios = listOf<Medio>()
    private val ingresos = mutableListOf<Ingreso>()

    private companion object {
        const val CONFIRMAR_SALIDA = "confirmar_salida"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAjustesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepararNavegacion(binding.navegacion.barraNavegacion, R.id.navAjustes)
        binding.btnNuevoFijo.setOnClickListener { abrirHojaFijo(null) }
        binding.btnNuevoIngreso.setOnClickListener { abrirHojaIngreso(null) }
        binding.btnNuevoMedio.setOnClickListener { abrirHojaMedio(null) }
        binding.btnExportar.setOnClickListener { exportar() }
        binding.btnSalir.setOnClickListener { confirmarSalida() }
        binding.btnVincular.setOnClickListener { vincularConGoogle() }

        escucharHojas()

        // Quien está probando sin cuenta tiene que poder pasarse a una de verdad
        // desde acá. Antes la única salida era cerrar sesión, y ahí perdía todo.
        binding.tarjetaInvitado.visibility =
            if (esInvitado()) View.VISIBLE else View.GONE
    }

    /**
     * Queda a la espera de lo que decidan las hojas y la pregunta de salida.
     *
     * Pasa por el canal de resultados y no por una función suelta para que la
     * respuesta llegue igual cuando la pantalla se rehizo mientras estaban abiertas.
     */
    private fun escucharHojas() {
        listOf(IngresoSheet.PEDIDO, FijoSheet.PEDIDO, MedioSheet.PEDIDO).forEach { pedido ->
            supportFragmentManager.setFragmentResultListener(pedido, this) { _, datos ->
                avisar(datos.getInt(HojaBase.MENSAJE))
            }
        }
        supportFragmentManager.setFragmentResultListener(CONFIRMAR_SALIDA, this) { _, _ ->
            cerrarSesion()
        }
    }

    override fun onStart() {
        super.onStart()
        escuchas += fijosRepo.escuchar { recibidos ->
            fijos = recibidos
            dibujarFijos()
        }
        escuchas += categoriasRepo.escuchar { recibidas ->
            categorias = recibidas
            dibujarFijos()
        }
        escuchas += ingresosRepo.escuchar { recibidos ->
            ingresos.clear()
            ingresos.addAll(recibidos)
            dibujarIngresos()
        }
        escuchas += gastosRepo.escuchar { recibidos -> gastos = recibidos }
        escuchas += mediosRepo.escuchar { recibidos ->
            medios = recibidos
            dibujarMedios()
        }
    }

    override fun onStop() {
        super.onStop()
        escuchas.forEach { it.remove() }
        escuchas.clear()
    }

    // ---------------------------------------------------------------- ingresos

    private fun dibujarIngresos() {
        binding.listaIngresos.removeAllViews()
        binding.vacioIngresos.visibility = if (ingresos.isEmpty()) View.VISIBLE else View.GONE

        ingresos.take(12).forEach { ingreso ->
            val fila = FilaIngresoBinding.inflate(
                LayoutInflater.from(this), binding.listaIngresos, false
            )
            fila.tvDetalle.text = ingreso.detalle.ifBlank { getString(R.string.ingreso) }
            fila.tvFecha.text = formatoFecha.format(ingreso.fecha)
            fila.tvMonto.text = Plata.formatear(ingreso.monto)
            fila.root.setOnClickListener { abrirHojaIngreso(ingreso) }
            fila.btnBorrar.setOnClickListener {
                ingresosRepo.borrar(ingreso.id)
                avisar(R.string.ingreso_borrado)
            }
            binding.listaIngresos.addView(fila.root)
        }
    }

    private fun abrirHojaIngreso(ingreso: Ingreso?) {
        IngresoSheet.abrir(ingreso).show(supportFragmentManager, IngresoSheet.PEDIDO)
    }

    // --------------------------------------------------------- medios de pago

    private fun dibujarMedios() {
        binding.listaMedios.removeAllViews()
        binding.vacioMedios.visibility = if (medios.isEmpty()) View.VISIBLE else View.GONE

        medios.forEach { medio ->
            val fila = FilaMedioBinding.inflate(
                LayoutInflater.from(this), binding.listaMedios, false
            )
            fila.icono.setImageResource(medio.iconoDibujable())
            fila.icono.imageTintList = ColorStateList.valueOf(medio.colorEntero())
            fila.fondoIcono.setCardBackgroundColor(medio.colorSuave())
            fila.tvNombre.text = medio.nombre
            fila.root.setOnClickListener { abrirHojaMedio(medio) }
            fila.btnBorrar.setOnClickListener {
                mediosRepo.borrar(medio.id)
                avisar(R.string.medio_borrado)
            }
            binding.listaMedios.addView(fila.root)
        }
    }

    private fun abrirHojaMedio(medio: Medio?) {
        MedioSheet.abrir(medio, medios.size).show(supportFragmentManager, MedioSheet.PEDIDO)
    }

    // ----------------------------------------------------------- gastos fijos

    private fun dibujarFijos() {
        binding.listaFijos.removeAllViews()
        binding.vacioFijos.visibility = if (fijos.isEmpty()) View.VISIBLE else View.GONE
        val porId = categorias.associateBy { it.id }

        fijos.forEach { fijo ->
            val fila = FilaFijoBinding.inflate(LayoutInflater.from(this), binding.listaFijos, false)
            val categoria = porId[fijo.categoriaId] ?: Categoria.desconocida()
            fila.icono.setImageResource(categoria.iconoDibujable())
            fila.icono.imageTintList =
                ColorStateList.valueOf(categoria.colorEntero())
            fila.fondoIcono.setCardBackgroundColor(categoria.colorSuave())
            fila.tvNombre.text = fijo.nombre
            fila.tvDia.text = getString(R.string.todos_los_dias, fijo.diaDelMes)
            fila.tvMonto.text = Plata.formatear(fijo.monto)
            fila.root.setOnClickListener { abrirHojaFijo(fijo) }
            fila.btnBorrar.setOnClickListener {
                fijosRepo.borrar(fijo.id)
                avisar(R.string.fijo_borrado)
            }
            binding.listaFijos.addView(fila.root)
        }
    }

    private fun abrirHojaFijo(fijo: GastoFijo?) {
        if (categorias.isEmpty()) {
            avisar(R.string.sin_categorias)
            return
        }
        FijoSheet.abrir(fijo, categorias).show(supportFragmentManager, FijoSheet.PEDIDO)
    }

    // -------------------------------------------------------------- exportar

    /**
     * Arma una planilla con todos los gastos y la manda a donde el usuario quiera.
     *
     * Se comparte como texto en lugar de escribir un archivo, que obligaría a declarar
     * un proveedor de archivos y permisos. Llega igual a correo, mensajes o notas.
     */
    private fun exportar() {
        if (gastos.isEmpty()) {
            avisar(R.string.sin_gastos_exportar)
            return
        }

        val porId = categorias.associateBy { it.id }
        val mediosPorId = medios.associateBy { it.id }
        val formato = SimpleDateFormat("yyyy-MM-dd", Idioma.español)
        val planilla = buildString {
            appendLine("fecha;monto;categoria;medio;detalle")
            gastos.forEach { gasto ->
                val categoria = porId[gasto.categoriaId]?.nombre ?: ""
                val medio = mediosPorId[gasto.medioId]?.nombre.orEmpty()
                // El punto y coma separa columnas, así que no puede quedar en el texto.
                val detalle = gasto.detalle.replace(';', ',')
                appendLine(
                    "${formato.format(gasto.fecha)};${paraPlanilla(gasto.monto)};" +
                        "$categoria;$medio;$detalle"
                )
            }
        }

        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.exportar_asunto))
            putExtra(Intent.EXTRA_TEXT, planilla)
        }
        startActivity(Intent.createChooser(envio, getString(R.string.exportar)))
    }

    /**
     * Escribe el importe como lo espera una planilla en español, con coma decimal y
     * sin decimales cuando son cero. Sin esto salía "6500.0" y quedaba como texto.
     */
    private fun paraPlanilla(monto: Double): String =
        if (monto % 1.0 == 0.0) monto.toLong().toString()
        else String.format(Idioma.español, "%.2f", monto)

    // ------------------------------------------------------- entrar con cuenta

    private fun esInvitado() = FirebaseAuth.getInstance().currentUser?.isAnonymous == true

    /** Suma una cuenta de Google a la sesión de invitado, conservando lo cargado. */
    private fun vincularConGoogle() {
        mostrarEsperaVinculo(true)
        lifecycleScope.launch {
            try {
                val resultado = acceso.entrar()
                avisar(
                    if (resultado == AccesoGoogle.Resultado.YA_EXISTIA) {
                        R.string.cuenta_ya_existia
                    } else {
                        R.string.cuenta_vinculada
                    }
                )
                // La pantalla anterior tiene que volver a leer, ahora con otro usuario.
                startActivity(
                    Intent(this@AjustesActivity, PrincipalActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            } catch (e: GetCredentialCancellationException) {
                mostrarEsperaVinculo(false)
            } catch (e: Exception) {
                mostrarEsperaVinculo(false)
                avisar(R.string.error_entrada)
            }
        }
    }

    private fun mostrarEsperaVinculo(esperando: Boolean) {
        binding.progresoVincular.visibility = if (esperando) View.VISIBLE else View.GONE
        binding.btnVincular.isEnabled = !esperando
        binding.btnVincular.text = if (esperando) "" else getString(R.string.btn_entrar)
        binding.btnVincular.icon = if (esperando) {
            null
        } else {
            ContextCompat.getDrawable(this, R.drawable.ic_google)
        }
    }

    // ----------------------------------------------------------- cerrar sesión

    private fun confirmarSalida() {
        // Al invitado hay que avisarle que se lleva los datos puestos.
        Confirmacion.pedir(
            pedido = CONFIRMAR_SALIDA,
            titulo = getString(R.string.btn_salir),
            aviso = if (esInvitado()) R.string.salir_invitado_aviso else R.string.salir_aviso,
            accion = R.string.btn_salir
        ).show(supportFragmentManager, CONFIRMAR_SALIDA)
    }

    private fun cerrarSesion() {
        escuchas.forEach { it.remove() }
        escuchas.clear()
        FirebaseAuth.getInstance().signOut()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun avisar(mensaje: Int) {
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_SHORT).show()
    }
}
