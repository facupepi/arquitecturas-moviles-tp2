package ar.edu.utn.frsfco.finanzas

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Las fichas con las que se elige una categoría o un medio de pago.
 *
 * Las dos se ven y se comportan igual, así que el dibujo vive acá y no repetido en
 * cada hoja. Incluye también los selectores de color y de ícono, que son los mismos
 * en la hoja de categorías y en la de medios.
 */
object Fichas {

    /**
     * Llena el grupo con una ficha por elemento y avisa cuál se tocó.
     *
     * La ficha elegida se llena con su propio color; el resto queda en blanco con
     * borde gris. Antes el fondo era fijo y no se distinguía cuál estaba marcada.
     */
    fun <T : Etiqueta> armar(
        grupo: ChipGroup,
        elementos: List<T>,
        elegidoId: String?,
        alElegir: (T) -> Unit
    ) {
        val contexto = grupo.context
        grupo.removeAllViews()
        elementos.forEach { elemento ->
            val chip = Chip(contexto).apply {
                text = elemento.nombre
                isCheckable = true
                isChecked = elemento.id == elegidoId
                chipIcon = ContextCompat.getDrawable(contexto, elemento.iconoDibujable())
                chipIconTint = ColorStateList.valueOf(elemento.colorEntero())
                typeface = ResourcesCompat.getFont(contexto, R.font.poppins_medium)
                setOnClickListener { alElegir(elemento) }
            }
            pintar(chip, elemento, elemento.id == elegidoId)
            grupo.addView(chip)
        }
    }

    /** Repinta todas las fichas del grupo según cuál quedó elegida. */
    fun <T : Etiqueta> repintar(grupo: ChipGroup, elementos: List<T>, elegidoId: String?) {
        elementos.forEachIndexed { posicion, elemento ->
            val chip = grupo.getChildAt(posicion) as Chip
            val esta = elemento.id == elegidoId
            chip.isChecked = esta
            pintar(chip, elemento, esta)
        }
    }

    private fun pintar(chip: Chip, elemento: Etiqueta, esta: Boolean) {
        val contexto = chip.context
        chip.chipBackgroundColor = ColorStateList.valueOf(
            if (esta) elemento.colorSuave() else color(contexto, R.color.superficie)
        )
        chip.chipStrokeWidth = if (esta) 4f else 2f
        chip.chipStrokeColor = ColorStateList.valueOf(
            if (esta) elemento.colorEntero() else color(contexto, R.color.borde)
        )
        chip.setTextColor(
            if (esta) elemento.colorEntero() else color(contexto, R.color.texto)
        )
    }

    // ------------------------------------------------- selectores de la hoja

    /** Una ficha por color de la paleta. */
    fun armarColores(grupo: ChipGroup, elegido: () -> String, alElegir: (String) -> Unit) {
        val contexto = grupo.context
        grupo.removeAllViews()
        Iconos.colores.forEach { hex ->
            val ficha = Chip(contexto)
            ficha.text = ""
            ficha.isCheckable = false
            ficha.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(hex))
            ficha.chipStrokeColor = ColorStateList.valueOf(color(contexto, R.color.texto))
            ficha.setOnClickListener {
                alElegir(hex)
                marcarColor(grupo, elegido())
            }
            grupo.addView(ficha)
        }
        marcarColor(grupo, elegido())
    }

    /** Al color elegido se le pone un borde oscuro; es lo único que lo distingue. */
    fun marcarColor(grupo: ChipGroup, elegido: String) {
        Iconos.colores.forEachIndexed { posicion, hex ->
            (grupo.getChildAt(posicion) as Chip).chipStrokeWidth = if (hex == elegido) 6f else 0f
        }
    }

    /** Una ficha por ícono del catálogo. */
    fun armarIconos(grupo: ChipGroup, elegido: () -> String, alElegir: (String) -> Unit) {
        val contexto = grupo.context
        grupo.removeAllViews()
        Iconos.disponibles.forEach { (clave, dibujable) ->
            val ficha = Chip(contexto)
            ficha.text = ""
            ficha.isCheckable = false
            ficha.chipIcon = ContextCompat.getDrawable(contexto, dibujable)
            ficha.chipIconTint = ColorStateList.valueOf(color(contexto, R.color.texto))
            ficha.chipBackgroundColor = ColorStateList.valueOf(color(contexto, R.color.superficie))
            ficha.setOnClickListener {
                alElegir(clave)
                marcarIcono(grupo, elegido())
            }
            grupo.addView(ficha)
        }
        marcarIcono(grupo, elegido())
    }

    /** El ícono elegido se marca con un borde verde, más grueso que el de los demás. */
    fun marcarIcono(grupo: ChipGroup, elegido: String) {
        Iconos.disponibles.forEachIndexed { posicion, (clave, _) ->
            val ficha = grupo.getChildAt(posicion) as Chip
            val esta = clave == elegido
            ficha.chipStrokeWidth = if (esta) 4f else 2f
            ficha.chipStrokeColor = ColorStateList.valueOf(
                color(grupo.context, if (esta) R.color.verde else R.color.borde)
            )
        }
    }

    private fun color(contexto: Context, @ColorRes recurso: Int) =
        ContextCompat.getColor(contexto, recurso)
}
