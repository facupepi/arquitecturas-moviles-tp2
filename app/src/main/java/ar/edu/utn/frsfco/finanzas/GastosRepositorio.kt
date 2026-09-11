package ar.edu.utn.frsfco.finanzas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Guarda y lee los gastos en Firestore.
 *
 *     usuarios/{uid}/gastos/{gasto}
 *
 * Cada usuario tiene su propia colección, colgada de su identificador, de modo que
 * las reglas de seguridad puedan limitar el acceso a los datos propios.
 */
class GastosRepositorio {

    private val base = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun coleccion() = base
        .collection("usuarios")
        .document(auth.currentUser?.uid ?: error("no hay sesión abierta"))
        .collection("gastos")

    suspend fun agregar(monto: Double, categoriaId: String, detalle: String, fecha: Date = Date()) {
        coleccion().add(aMapa(monto, categoriaId, detalle, fecha)).await()
    }

    /** Reemplaza los datos de un gasto que el usuario corrigió. */
    suspend fun actualizar(id: String, monto: Double, categoriaId: String, detalle: String, fecha: Date) {
        coleccion().document(id).set(aMapa(monto, categoriaId, detalle, fecha)).await()
    }

    suspend fun borrar(id: String) {
        coleccion().document(id).delete().await()
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
                        fecha = doc.getDate("fecha") ?: Date()
                    )
                })
            }

    private fun aMapa(monto: Double, categoriaId: String, detalle: String, fecha: Date) =
        hashMapOf(
            "monto" to monto,
            "categoria" to categoriaId,
            "detalle" to detalle.trim(),
            "fecha" to fecha
        )
}
