package ar.edu.utn.frsfco.finanzas

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * La entrada con cuenta de Google.
 *
 * Vive aparte porque se usa desde dos lados: la pantalla de entrada y los ajustes,
 * donde quien venía probando sin cuenta puede pasarse a una de verdad.
 */
class AccesoGoogle(private val pantalla: Activity) {

    private val auth = FirebaseAuth.getInstance()
    private val credenciales by lazy { CredentialManager.create(pantalla) }

    /** Qué pasó al entrar, para que cada pantalla avise lo que corresponda. */
    enum class Resultado {
        /** Entró normalmente, o sumó la cuenta a la sesión de invitado. */
        ENTRO,

        /**
         * La cuenta elegida ya tenía datos de antes, así que no se pudo sumar a la
         * sesión de invitado y se entró a la de siempre. Lo cargado como invitado
         * queda en la sesión anónima y no se ve más.
         */
        YA_EXISTIA
    }

    companion object {
        private const val TAG = "TP2-Login"
    }

    /**
     * Le pide al sistema una cuenta de Google. El identificador que se envía es el
     * cliente web del proyecto, que el complemento de Google Services genera a
     * partir de google-services.json.
     */
    private suspend fun pedirCuenta(): GoogleIdTokenCredential {
        val opcion = GetGoogleIdOption.Builder()
            .setServerClientId(pantalla.getString(R.string.default_web_client_id))
            // En falso para que muestre todas las cuentas del teléfono, no sólo
            // las que ya usaron esta aplicación.
            .setFilterByAuthorizedAccounts(false)
            .build()

        val pedido = GetCredentialRequest.Builder()
            .addCredentialOption(opcion)
            .build()

        val respuesta = credenciales.getCredential(pantalla, pedido)
        return GoogleIdTokenCredential.createFrom(respuesta.credential.data)
    }

    /**
     * Abre la sesión con la cuenta elegida.
     *
     * Si venía probando sin cuenta, la de Google se suma a la sesión anónima para no
     * perder los gastos ya cargados. Cuando esa cuenta ya tiene datos propios de
     * antes, Firebase rechaza la unión y se entra a la cuenta vieja, que es la que
     * tiene el historial de verdad.
     */
    suspend fun entrar(): Resultado {
        val credencial = pedirCuenta()
        val paraFirebase = GoogleAuthProvider.getCredential(credencial.idToken, null)
        val invitado = auth.currentUser?.isAnonymous == true

        if (!invitado) {
            val resultado = auth.signInWithCredential(paraFirebase).await()
            Log.d(TAG, "sesión iniciada como ${resultado.user?.email}")
            return Resultado.ENTRO
        }

        return try {
            val resultado = auth.currentUser!!.linkWithCredential(paraFirebase).await()
            Log.d(TAG, "cuenta sumada a la sesión de invitado: ${resultado.user?.email}")
            Resultado.ENTRO
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.d(TAG, "la cuenta ya existía, se entra a la de siempre")
            auth.signInWithCredential(paraFirebase).await()
            Resultado.YA_EXISTIA
        }
    }
}
