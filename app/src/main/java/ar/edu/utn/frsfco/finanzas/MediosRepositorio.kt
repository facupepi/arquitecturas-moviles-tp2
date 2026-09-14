package ar.edu.utn.frsfco.finanzas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Guarda los medios de pago de cada usuario.
 *
 *     usuarios/{uid}/medios/{medio}
 *
 * La primera vez que alguien entra se siembran los tres habituales, con las mismas
 * claves que ya traían los gastos cargados, para que ninguno quede sin medio.
 */
class MediosRepositorio {

    private val base = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun coleccion() = base
        .collection("usuarios")
        .document(auth.currentUser?.uid ?: error("no hay sesión abierta"))
        .collection("medios")

    /** Crea los medios iniciales si el usuario todavía no tiene ninguno. */
    fun sembrarSiHaceFalta() {
        // Alcanza con pedir uno: si aparece, es que ya están sembrados.
        coleccion().limit(1).get().addOnSuccessListener { resultado ->
            if (!resultado.isEmpty) return@addOnSuccessListener

            val lote = base.batch()
            Medio.iniciales().forEach { medio ->
                lote.set(coleccion().document(medio.id), aMapa(medio))
            }
            lote.commit()
        }
    }

    fun escuchar(alCambiar: (List<Medio>) -> Unit): ListenerRegistration =
        coleccion().addSnapshotListener { documentos, error ->
            if (error != null || documentos == null) return@addSnapshotListener
            alCambiar(documentos.map { doc ->
                Medio(
                    id = doc.id,
                    nombre = doc.getString("nombre").orEmpty(),
                    color = doc.getString("color") ?: "#5C7CFA",
                    icono = doc.getString("icono") ?: "billetera",
                    orden = (doc.getLong("orden") ?: 0L).toInt()
                )
            }.sortedBy { it.orden })
        }

    fun guardar(medio: Medio) {
        if (medio.id.isBlank()) {
            coleccion().add(aMapa(medio))
        } else {
            coleccion().document(medio.id).set(aMapa(medio))
        }
    }

    fun borrar(id: String) {
        coleccion().document(id).delete()
    }

    private fun aMapa(m: Medio) = hashMapOf(
        "nombre" to m.nombre.trim(),
        "color" to m.color,
        "icono" to m.icono,
        "orden" to m.orden
    )
}
