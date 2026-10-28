package fr.mamaison.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable internal fun MaisonScreen(navigate: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionHeading("Équipements", "Deux espaces distincts pour vos objets connectés") }
        item { MenuCard("Appareils locaux", "Shelly · découverte, éclairage et prises", MaisonIcons.Devices) { navigate("Appareils") } }
        item { MenuCard("Google Home", "Votre compte Google et ses autorisations", MaisonIcons.Link) { navigate("Google Home") } }
        item { SectionHeading("Caméras", "Les webcams USB de votre serveur") }
        item { MenuCard("Mes caméras", "Aperçus, ajout et accès au direct", MaisonIcons.Videocam) { navigate("Caméras") } }
        item { SectionHeading("Pièces et scènes", "Organiser et commander les appareils locaux") }
        item { MenuCard("Pièces et scènes", "Affectations et actions groupées", MaisonIcons.Home) { navigate("Pièces et scènes") } }
    }
}

@Composable internal fun StorageScreen(stats: JSONObject, loading: Boolean, error: String, refresh: () -> Unit) {
    val storage = stats.optJSONObject("storage")
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { StatusCard("Stockage du serveur", "Le volume qui accueille vos fichiers", MaisonIcons.Storage) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error.isNotBlank()) item { Notice(error,isError=true) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Espace disponible", color = MaisonMuted)
                    Text(if (storage != null && storage.has("free")) bytes(storage.optDouble("free")) else "Indisponible", style = MaterialTheme.typography.headlineMedium)
                    if (storage != null && storage.has("total") && storage.optDouble("total") > 0) {
                        val total = storage.optDouble("total")
                        val free = storage.optDouble("free")
                        LinearProgressIndicator(progress = { ((total - free) / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${bytes(total - free)} utilisés sur ${bytes(total)}", color = MaisonMuted)
                    }
                    Text("Ces informations concernent le stockage partagé. Les disques et partitions se gèrent sur le serveur.", style = MaterialTheme.typography.bodySmall, color = MaisonMuted)
                }
            }
        }
        item { OutlinedButton(onClick = refresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Actualiser le stockage") } }
    }
}

internal fun readableConnection(label: String): String = when {
    label.contains("locale", ignoreCase = true) -> "À la maison · connexion locale"
    label.contains("5G", ignoreCase = true) || label.contains("distante", ignoreCase = true) -> "Hors de la maison · connexion distante"
    else -> "Connexion au serveur"
}
