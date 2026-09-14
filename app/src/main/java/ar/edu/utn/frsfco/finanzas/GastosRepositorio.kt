package ar.edu.utn.frsfco.finanzas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

/**
 * Guarda y lee los gastos en Firestore.
 *
 *     usuarios/{uid}/gastos/{gasto}
 *
 * Cada usuario tiene su propia colección, colgada de su identificador, de modo que
 * las reglas de seguridad puedan limitar el acceso a los datos propios.
 *
 * Ninguna escritura espera la confirmación del servidor. Firestore guarda primero en
 * el teléfono y sincroniza cuando puede, así que esperar dejaba la pantalla trabada
 * sin señal aunque el dato ya estuviera guardado.
 */
class GastosRepositorio {

    private val base = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun coleccion() = base
        .collection("usuarios")
        .document(auth.currentUser?.uid ?: error("no hay sesión abierta"))
        .collection("gastos")

    fun agregar(gasto: Gasto) {
        coleccion().add(aMapa(gasto))
    }

    /** Reemplaza los datos de un gasto que el usuario corrigió. */
    fun actualizar(gasto: Gasto) {
        coleccion().document(gasto.id).set(aMapa(gasto))
    }

    fun borrar(id: String) {
        coleccion().document(id).delete()
    }

    /** Vuelve a guardar un gasto recién borrado, con su mismo identificador. */
    fun restaurar(gasto: Gasto) {
        coleccion().document(gasto.id).set(aMapa(gasto))
    }

    /**
     * Escucha los gastos y avisa cada vez que cambian.
     *
     * Firestore mantiene una copia local, así que la lista aparece al instante y se
     * actualiza sola cuando termina de sincronizar. Devuelve el registro para poder
     * cortar la escucha cuando la pantalla deja de verse.
     */
    fun escuchar(alCambiar: (List<Gasto>) -> Unit): ListenerRegistration =
        coleccion()
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { documentos, error ->
                if (error != null || documentos == null) return@addSnapshotListener
                alCambiar(documentos.map { doc ->
                    Gasto(
                        id = doc.id,
                        monto = doc.getDouble("monto") ?: 0.0,
                        categoriaId = doc.getString("categoria").orEmpty(),
                        detalle = doc.getString("detalle").orEmpty(),
                        fecha = doc.getDate("fecha") ?: Date(),
                        // Los movimientos cargados antes guardaban la clave en "medioPago";
                        // los medios sembrados usan esas mismas claves como id.
                        medioId = (doc.getString("medioId")
                            ?: doc.getString("medioPago")).orEmpty(),
                        fijoId = doc.getString("fijoId").orEmpty()
                    )
                })
            }

    private fun aMapa(g: Gasto) = hashMapOf(
        "monto" to g.monto,
        "categoria" to g.categoriaId,
        "detalle" to g.detalle.trim(),
        "fecha" to g.fecha,
        "medioId" to g.medioId,
        "fijoId" to g.fijoId
    )
}
