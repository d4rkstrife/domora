package fr.mamaison.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class MobileRecovery(private val client: MaisonClient) {
    var state by mutableStateOf(MobileConnectionState()); private set
    var serverRaw by mutableStateOf(""); private set
    var generation by mutableIntStateOf(0); private set
    private val memory = MobileReadMemory()
    private val check = Mutex()
    fun cached(route: String) = memory.get(client.address, route)
    suspend fun read(route: String): String = client.get(route).also {
        try {
            val array=org.json.JSONArray(it)
            val identity=if(route=="library"||route=="media/catalog/library"||route.startsWith("files?path="))"path" else "id"
            for(index in 0 until array.length())array.getJSONObject(index).getString(identity)
            memory.put(client.address, route, it)
        } catch (_: org.json.JSONException) { }
    }
    fun clearMemory() { memory.clear(); serverRaw=""; state=MobileConnectionState() }

    suspend fun verify(explicitRetry: Boolean = false, refreshOnSuccess: Boolean = false) {
        if (!check.tryLock()) return
        val previous = state
        if (explicitRetry || previous.phase != MobileConnectionPhase.CONNECTED) state = state.begin()
        try {
            // Only an explicit retry renews a rejected session. Mutations are never replayed.
            if (explicitRetry && previous.phase == MobileConnectionPhase.SESSION) client.authenticate()
            val response = client.get("server")
            require(JSONObject(response).optString("id").isNotBlank()){ "Réponse du serveur incomplète. Réessayez la connexion." }
            serverRaw = response
            state = state.success(System.currentTimeMillis())
            if (explicitRetry || refreshOnSuccess || previous.phase != MobileConnectionPhase.CONNECTED) generation++
        } catch (e: CancellationException) { state = previous; throw e }
        catch (e: Exception) { state = state.failed(mobileFailure(e)) }
        finally { check.unlock() }
    }
}

internal val LocalMobileRecovery = staticCompositionLocalOf<MobileRecovery> { error("Mobile recovery not installed") }

@Composable internal fun MobileConnectionBanner(recovery: MobileRecovery, connectionLabel: String, retry: () -> Unit) {
    val state = recovery.state
    val online = state.phase == MobileConnectionPhase.CONNECTED
    val title = when (state.phase) {
        MobileConnectionPhase.CONNECTED -> "Connecté · ${if (connectionLabel.contains("locale", true)) "local" else "distant"}"
        MobileConnectionPhase.CHECKING -> "Reconnexion en cours…"
        MobileConnectionPhase.SESSION -> "Connexion à renouveler"
        MobileConnectionPhase.IDENTITY -> "Identité du serveur à vérifier"
        MobileConnectionPhase.UNREACHABLE -> "Serveur inaccessible"
        else -> "Connexion non vérifiée"
    }
    Surface(color = if (online) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(MaisonIcons.Network, null, tint = if (online) MaisonGreen else MaisonWarning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp)); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (!online) TextButton(onClick = retry, enabled = !state.checking, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Réessayer") }
            }
            if (!online && !state.checking) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaisonMuted)
            if (!online && state.lastSuccess != null) Text("Dernières données : ${DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(state.lastSuccess))} · conservées à l’écran", color = MaisonMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable internal fun RetryNotice(message: String, enabled: Boolean = true, retry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Notice(message, isError = true)
        Text("Les données affichées peuvent dater d’un précédent chargement.",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)
        TextButton(enabled = enabled, onClick = retry) { Text("Réessayer le chargement") }
    }
}
