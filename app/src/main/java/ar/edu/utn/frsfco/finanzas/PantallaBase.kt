package ar.edu.utn.frsfco.finanzas

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Base de las pantallas principales.
 *
 * Resuelve dos cosas que necesitan todas por igual: el idioma y el menú de abajo.
 */
abstract class PantallaBase : AppCompatActivity() {

    /**
     * Deja la pantalla en español de Argentina.
     *
     * Los textos propios están escritos en español, pero el calendario y los diálogos
     * los arma el sistema con el idioma del teléfono. En un equipo en inglés la fecha
     * salía como "September" en medio de una pantalla en castellano.
     */
    override fun attachBaseContext(base: Context) {
        // Se repone acá y no una sola vez al arrancar porque el sistema impone el
        // idioma del equipo cada vez que rehace la pantalla, por ejemplo al girarla:
        // sin esto el calendario volvía con los meses en inglés después de un giro.
        Idioma.fijar()
        val ajustada = Configuration(base.resources.configuration).apply {
            setLocale(Idioma.español)
        }
        super.attachBaseContext(base.createConfigurationContext(ajustada))
    }

    /**
     * Deja el menú de abajo listo, con la pestaña de esta pantalla marcada.
     *
     * Cada pestaña es una pantalla propia. Se reordena la que ya existe en lugar de
     * abrir otra, así el botón de volver del teléfono no encadena una detrás de otra,
     * y se apaga la animación para que se sienta como cambiar de pestaña.
     */
    protected fun prepararNavegacion(barra: BottomNavigationView, actual: Int) {
        barra.selectedItemId = actual
        barra.setOnItemSelectedListener { opcion ->
            if (opcion.itemId == actual) return@setOnItemSelectedListener true

            val destino = when (opcion.itemId) {
                R.id.navCategorias -> CategoriasActivity::class.java
                R.id.navAjustes -> AjustesActivity::class.java
                else -> PrincipalActivity::class.java
            }
            startActivity(
                Intent(this, destino).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
            overridePendingTransition(0, 0)

            // En falso para que esta barra NO marque el destino. Cada pantalla marca
            // la suya al abrirse; si además la marcara la que se está dejando, al
            // volver a ella por una instancia ya creada seguiría señalando la otra.
            false
        }
    }
}
