package fr.mamaison.app

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal enum class MobileFailureKind { NETWORK, IDENTITY, SESSION, PERMISSION, SERVICE }
internal data class MobileFailure(val kind: MobileFailureKind, val message: String)

internal fun mobileFailure(error: Exception, component: String = "serveur"): MobileFailure {
    val message = error.message.orEmpty()
    val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
    return when {
        causes.any { it is SSLException } || message.contains("identité", true) || message.contains("certificat", true) ->
            MobileFailure(MobileFailureKind.IDENTITY, "L’identité sécurisée du serveur ne peut pas être vérifiée. Vérifiez le serveur avant de vous reconnecter.")
        message.contains("Appairez votre appareil", true) || message.contains("Authentification refusée", true) || message.contains("Appareil non autorisé", true) || message == "Appairage requis" ->
            MobileFailure(MobileFailureKind.SESSION, "Votre session ou l’autorisation de ce téléphone n’est plus valide. Réessayez la connexion ; un accès retiré nécessite un nouvel appairage autorisé.")
        message.contains("propriétaire", true) || message.contains("réservé", true) || message.contains("non autorisée", true) ->
            MobileFailure(MobileFailureKind.PERMISSION, "Ce téléphone n’a pas l’autorisation pour cette fonction. Utilisez un appareil disposant des droits nécessaires.")
        causes.any { it is SocketTimeoutException || it is ConnectException || it is UnknownHostException || it is IOException } ->
            MobileFailure(MobileFailureKind.NETWORK, "Le serveur est injoignable. Vérifiez le réseau du téléphone et l’accès distant si vous êtes hors de la maison, puis réessayez.")
        else -> MobileFailure(MobileFailureKind.SERVICE, when {
            message.isBlank() -> "Le service $component ne répond pas correctement. Réessayez son chargement."
            component == "téléchargements" && message.contains("indisponible", true) -> "Le service de téléchargements est indisponible sur le serveur. Les autres fonctions peuvent rester accessibles."
            else -> message
        })
    }
}

internal enum class MobileConnectionPhase { UNKNOWN, CHECKING, CONNECTED, UNREACHABLE, SESSION, IDENTITY }
internal data class MobileConnectionState(
    val phase: MobileConnectionPhase = MobileConnectionPhase.UNKNOWN,
    val message: String = "Connexion non vérifiée", val lastSuccess: Long? = null
) {
    val checking get() = phase == MobileConnectionPhase.CHECKING
    fun begin() = copy(phase = MobileConnectionPhase.CHECKING, message = if (lastSuccess == null) "Connexion en cours…" else "Vérification / reconnexion en cours…")
    fun success(time: Long) = MobileConnectionState(MobileConnectionPhase.CONNECTED, "Connecté au serveur", time)
    fun failed(error: MobileFailure) = copy(phase = when (error.kind) {
        MobileFailureKind.IDENTITY -> MobileConnectionPhase.IDENTITY
        MobileFailureKind.SESSION, MobileFailureKind.PERMISSION -> MobileConnectionPhase.SESSION
        else -> MobileConnectionPhase.UNREACHABLE
    }, message = error.message)
}

/** RAM only, bounded, and restricted to explicit read-only lists; no tokens or configuration secrets. */
internal class MobileReadMemory(private val maxBytes: Int = 8 * 1024 * 1024) {
    private val entries = LinkedHashMap<String, String>(16, .75f, true)
    private var size = 0
    private fun allowed(route: String) = route in setOf("library", "media/catalog/library", "downloads", "cameras", "home/devices", "rooms", "scenes") || route.startsWith("files?path=")
    fun get(server: String, route: String): String? = entries["$server|$route"]
    fun put(server: String, route: String, value: String) {
        if (!allowed(route) || value.length.toLong() * 2 > maxBytes / 2) return
        val key = "$server|$route"
        entries.remove(key)?.let { size -= it.length * 2 }
        entries[key] = value; size += value.length * 2
        while (size > maxBytes || entries.size > 24) {
            val oldest = entries.entries.first(); size -= oldest.value.length * 2; entries.remove(oldest.key)
        }
    }
    fun clear() { entries.clear(); size = 0 }
}
