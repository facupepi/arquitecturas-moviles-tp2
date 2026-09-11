package ar.edu.utn.frsfco.finanzas

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import ar.edu.utn.frsfco.finanzas.databinding.ActivityLoginBinding
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Pantalla de entrada.
 *
 * Si ya hay una sesión abierta pasa directo al resumen. Si no, ofrece entrar con
 * una cuenta de Google usando el administrador de credenciales de Android.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val credenciales by lazy { CredentialManager.create(this) }

    companion object {
        private const val TAG = "TP2-Login"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEntrar.setOnClickListener { entrarConGoogle() }
        binding.btnAgregarCuenta.setOnClickListener { abrirAltaDeCuenta() }
    }

    override fun onStart() {
        super.onStart()
        // Firebase guarda la sesión, así que sólo se pide la cuenta la primera vez.
        if (auth.currentUser != null) {
            irAlResumen()
        }
    }

    private fun entrarConGoogle() {
        mostrarEspera(true)
        lifecycleScope.launch {
            try {
                val credencial = pedirCuentaDeGoogle()
                autenticarEnFirebase(credencial)
                irAlResumen()
            } catch (e: GetCredentialCancellationException) {
                // El usuario cerró el selector de cuentas. No es un error.
                Log.d(TAG, "el usuario canceló la elección de cuenta")
                mostrarEspera(false)
            } catch (e: NoCredentialException) {
                // El teléfono no tiene ninguna cuenta de Google. El sistema no permite
                // crearla desde acá, así que se ofrece abrir la pantalla que sí lo hace.
                mostrarEspera(false)
                binding.btnAgregarCuenta.visibility = View.VISIBLE
                avisar(getString(R.string.sin_cuentas))
            } catch (e: GetCredentialException) {
                mostrarEspera(false)
                Log.e(TAG, "falló el pedido de credenciales", e)
                avisar(getString(R.string.error_entrada))
            } catch (e: Exception) {
                mostrarEspera(false)
                Log.e(TAG, "falló la autenticación", e)
                avisar(getString(R.string.error_entrada))
            }
        }
    }

    /**
     * Le pide al sistema una cuenta de Google. El identificador que se envía es el
     * cliente web del proyecto, que el complemento de Google Services genera a
     * partir de google-services.json.
     */
    private suspend fun pedirCuentaDeGoogle(): GoogleIdTokenCredential {
        val opcion = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            // En falso para que muestre todas las cuentas del teléfono, no sólo
            // las que ya usaron esta aplicación.
            .setFilterByAuthorizedAccounts(false)
            .build()

        val pedido = GetCredentialRequest.Builder()
            .addCredentialOption(opcion)
            .build()

        val respuesta = credenciales.getCredential(this, pedido)
        return GoogleIdTokenCredential.createFrom(respuesta.credential.data)
    }

    /** Cambia el identificador de Google por una sesión de Firebase. */
    private suspend fun autenticarEnFirebase(credencial: GoogleIdTokenCredential) {
        val paraFirebase = GoogleAuthProvider.getCredential(credencial.idToken, null)
        val resultado = auth.signInWithCredential(paraFirebase).await()
        Log.d(TAG, "sesión iniciada como ${resultado.user?.email}")
    }

    /**
     * Abre la pantalla del sistema que agrega una cuenta de Google. Agregar la cuenta
     * es una tarea del sistema operativo y ninguna aplicación puede hacerla por su
     * cuenta, así que lo mejor posible es llevar al usuario hasta ahí.
     */
    private fun abrirAltaDeCuenta() {
        val alta = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
            putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
        }
        if (alta.resolveActivity(packageManager) != null) {
            startActivity(alta)
            return
        }
        // Algunos teléfonos no exponen esa pantalla. Se cae a los ajustes de cuentas.
        val ajustes = Intent(Settings.ACTION_SYNC_SETTINGS)
        if (ajustes.resolveActivity(packageManager) != null) {
            startActivity(ajustes)
        } else {
            avisar(getString(R.string.sin_pantalla_cuentas))
        }
    }

    private fun irAlResumen() {
        startActivity(Intent(this, PrincipalActivity::class.java))
        finish()
    }

    /**
     * Mientras se resuelve la entrada, el botón se queda sin texto ni ícono y en su
     * lugar gira el indicador. Como el indicador está encima del botón y no debajo,
     * la pantalla no se mueve.
     */
    private fun mostrarEspera(esperando: Boolean) {
        binding.progreso.visibility = if (esperando) View.VISIBLE else View.GONE
        binding.btnEntrar.isEnabled = !esperando
        binding.btnEntrar.text = if (esperando) "" else getString(R.string.btn_entrar)
        binding.btnEntrar.icon = if (esperando) {
            null
        } else {
            ContextCompat.getDrawable(this, R.drawable.ic_google)
        }
    }

    private fun avisar(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
