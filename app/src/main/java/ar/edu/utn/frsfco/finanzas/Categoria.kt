package ar.edu.utn.frsfco.finanzas

import android.graphics.Color
import java.io.Serializable

/**
 * Lo que se elige con una ficha de color e ícono.
 *
 * Las categorías y los medios de pago se muestran igual en toda la aplicación, así que
 * las pantallas trabajan contra esta interfaz y no contra cada uno por separado.
 */
interface Etiqueta {
    val id: String
    val nombre: String
    fun colorEntero(): Int
    fun colorSuave(): Int
    fun iconoDibujable(): Int
}

/**
 * Una categoría de gasto.
 *
 * Cada usuario tiene las suyas y puede cambiarlas, así que se guardan en Firestore
 * y no en el código. Las cinco primeras se siembran al entrar por primera vez.
 */
data class Categoria(
    override val id: String = "",
    override val nombre: String = "",
    /** Color en formato #RRGGBB. Se guarda como texto para poder editarlo. */
    val color: String = "#5C7CFA",
    /** Clave del ícono dentro del catálogo disponible. */
    val icono: String = "otros",
    val orden: Int = 0
) : Etiqueta, Serializable {

    /** El color de la categoría, o el de reserva si el guardado no se puede leer. */
    override fun colorEntero(): Int = try {
        Color.parseColor(color)
    } catch (e: IllegalArgumentException) {
        Color.parseColor("#5C7CFA")
    }

    /** El mismo color pero muy claro, para el fondo del ícono. */
    override fun colorSuave(): Int {
        val base = colorEntero()
        return Color.argb(
            28,
            Color.red(base),
            Color.green(base),
            Color.blue(base)
        )
    }

    override fun iconoDibujable(): Int = Iconos.porClave(icono)

    companion object {
        /** Las que se crean la primera vez, para que la aplicación sirva de entrada. */
        fun iniciales(): List<Categoria> = listOf(
            Categoria("comida", "Comida", "#E8590C", "comida", 0),
            Categoria("transporte", "Transporte", "#1C7ED6", "transporte", 1),
            Categoria("servicios", "Servicios", "#0CA678", "servicios", 2),
            Categoria("ocio", "Ocio", "#9C36B5", "ocio", 3),
            Categoria("otros", "Otros", "#5C7CFA", "otros", 4)
        )

        /** Se muestra cuando un gasto quedó apuntando a una categoría borrada. */
        fun desconocida() = Categoria("", "Sin categoría", "#8A8A8A", "otros", 99)
    }
}

/** Íconos que el usuario puede elegir al crear una categoría. */
object Iconos {

    val disponibles = listOf(
        "comida" to R.drawable.ic_comida,
        "tarjeta" to R.drawable.ic_tarjeta,
        "transporte" to R.drawable.ic_transporte,
        "servicios" to R.drawable.ic_servicios,
        "ocio" to R.drawable.ic_ocio,
        "compras" to R.drawable.ic_compras,
        "billetera" to R.drawable.ic_billetera,
        "persona" to R.drawable.ic_persona,
        "otros" to R.drawable.ic_otros
    )

    /** Colores sugeridos, elegidos para que se distingan entre sí en la lista. */
    val colores = listOf(
        "#E8590C", "#1C7ED6", "#0CA678", "#9C36B5",
        "#5C7CFA", "#D6336C", "#F08C00", "#495057"
    )

    fun porClave(clave: String): Int =
        disponibles.firstOrNull { it.first == clave }?.second ?: R.drawable.ic_otros
}
