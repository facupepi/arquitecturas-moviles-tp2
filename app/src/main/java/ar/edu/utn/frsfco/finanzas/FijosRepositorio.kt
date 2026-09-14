package ar.edu.utn.frsfco.finanzas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date

/**
 * Guarda los gastos que se repiten todos los meses.
 *
 *     usuarios/{uid}/fijos/{fijo}
 *
 * El alquiler y las expensas eran los dos gastos más grandes y los más aburridos de
 * cargar, y olvidarse uno dejaba el total del mes muy por debajo de la realidad.
 */
class FijosRepositorio {

    private val base = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun coleccion() = base
        .collection("usuarios")
        .document(auth.currentUser?.uid ?: error("no hay sesión abierta"))
        .collection("fijos")

    fun guardar(fijo: GastoFijo) {
        if (fijo.id.isBlank()) {
            coleccion().add(aMapa(fijo))
        } else {
            coleccion().document(fijo.id).set(aMapa(fijo))
        }
    }

    fun borrar(id: String) {
        coleccion().document(id).delete()
    }

    fun escuchar(alCambiar: (List<GastoFijo>) -> Unit): ListenerRegistration =
        coleccion().addSnapshotListener { documentos, error ->
            if (error != null || documentos == null) return@addSnapshotListener
            alCambiar(documentos.map { doc ->
                GastoFijo(
                    id = doc.id,
                    nombre = doc.getString("nombre").orEmpty(),
                    monto = doc.getDouble("monto") ?: 0.0,
                    categoriaId = doc.getString("categoria").orEmpty(),
                    // Los movimientos cargados antes guardaban la clave en "medioPago";
                        // los medios sembrados usan esas mismas claves como id.
                        medioId = (doc.getString("medioId")
                            ?: doc.getString("medioPago")).orEmpty(),
                    diaDelMes = (doc.getLong("diaDelMes") ?: 1L).toInt()
                )
            }.sortedBy { it.diaDelMes })
        }

    companion object {

        /**
         * Crea los gastos de este mes que todavía faltan.
         *
         * Se apoya en los gastos que ya están cargados en lugar de llevar una marca
         * aparte, así que no puede duplicar nada aunque se ejecute muchas veces.
         * Sólo alcanza a los fijos cuyo día ya pasó.
         */
        fun materializar(
            fijos: List<GastoFijo>,
            gastos: List<Gasto>,
            repositorio: GastosRepositorio
        ) {
            val hoy = Calendar.getInstance()
            val yaCreados = gastos
                .filter { it.fijoId.isNotBlank() && esDeEsteMes(it.fecha, hoy) }
                .map { it.fijoId }
                .toSet()

            fijos.forEach { fijo ->
                if (fijo.id in yaCreados) return@forEach
                if (fijo.diaDelMes > hoy.get(Calendar.DAY_OF_MONTH)) return@forEach

                repositorio.agregar(
                    Gasto(
                        monto = fijo.monto,
                        categoriaId = fijo.categoriaId,
                        detalle = fijo.nombre,
                        fecha = elDiaDeEsteMes(fijo.diaDelMes),
                        medioId = fijo.medioId,
                        fijoId = fijo.id
                    )
                )
            }
        }

        /** Si el mes es más corto que el día pedido, cae en el último día. */
        private fun elDiaDeEsteMes(dia: Int): Date = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, minOf(dia, getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        private fun esDeEsteMes(fecha: Date, hoy: Calendar): Boolean {
            val otra = Calendar.getInstance().apply { time = fecha }
            return otra.get(Calendar.YEAR) == hoy.get(Calendar.YEAR) &&
                otra.get(Calendar.MONTH) == hoy.get(Calendar.MONTH)
        }
    }

    private fun aMapa(f: GastoFijo) = hashMapOf(
        "nombre" to f.nombre.trim(),
        "monto" to f.monto,
        "categoria" to f.categoriaId,
        "medioId" to f.medioId,
        "diaDelMes" to f.diaDelMes
    )
}
