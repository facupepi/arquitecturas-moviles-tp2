package ar.edu.utn.frsfco.finanzas

import java.util.Date

/**
 * Un gasto cargado por el usuario.
 *
 * En Firestore el campo se sigue llamando `categoria` y guarda el identificador de
 * la categoría elegida, que ahora es un documento editable y no un valor fijo.
 */
data class Gasto(
    val id: String = "",
    val monto: Double = 0.0,
    val categoriaId: String = "",
    val detalle: String = "",
    val fecha: Date = Date()
)
