package fr.mamaison.app

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

private object MobileArtworkCache {
    val requests = Semaphore(2)
    val images = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}

private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key)
private fun parseMobileVideos(raw: String): List<MobileLibraryVideo> = JSONArray(raw).objects().map { video ->
    val meta = video.optJSONObject("metadata")
    val progress = video.optJSONObject("progress")
    MobileLibraryVideo(
        path = video.getString("path"), title = video.optString("title").ifBlank { video.getString("path").substringAfterLast('/') },
        size = video.optDouble("size", 0.0), position = progress?.optDouble("position", 0.0) ?: 0.0,
        duration = progress?.optDouble("duration", 0.0) ?: 0.0,
        metadata = meta?.let { MobileMovieMetadata(
            title = it.text("title"), year = it.text("year"), overview = it.text("overview"),
            genres = it.optJSONArray("genres")?.let { array -> (0 until array.length()).map { index -> array.optString(index) } } ?: emptyList(),
            runtimeMinutes = it.optInt("runtime", 0).coerceAtLeast(0), poster = it.text("poster"), backdrop = it.text("backdrop")
        ) }, posterReady = video.optBoolean("posterReady")
    )
}

@Composable private fun mobileArtwork(client: MaisonClient, video: MobileLibraryVideo, wide: Boolean, generation: Int): Bitmap? {
    val asset = if (wide) video.metadata?.backdrop.orEmpty() else video.metadata?.poster.orEmpty()
    // An in-memory image is scoped to this client and server, never a globally reusable URL/token.
    val key = "${System.identityHashCode(client)}|${client.address}|${video.path}|$wide|$asset"
    var image by remember(key) { mutableStateOf(MobileArtworkCache.images.get(key)) }
    LaunchedEffect(key, generation, video.posterReady) {
        if (image != null || asset.isBlank() || (!wide && !video.posterReady)) return@LaunchedEffect
        try {
            MobileArtworkCache.requests.withPermit {
                image = MobileArtworkCache.images.get(key) ?: client.poster(video.path, tv = false, backdrop = wide)
                image?.let { MobileArtworkCache.images.put(key, it) }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            // A missing image must never prevent opening or playing the actual video.
        }
    }
    return image
}

@Composable fun LibraryScreen(client: MaisonClient, play: (String) -> Unit, onCatalog: () -> Unit) {
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf(emptyList<MobileLibraryVideo>()) }
    var message by remember { mutableStateOf("") }
    var catalogNotice by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var generation by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Tous") }
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    suspend fun refresh() {
        loading = true; message = ""; catalogNotice = ""
        try {
            val basic = parseMobileVideos(client.get("library"))
            videos = basic
            generation++
            if (basic.isNotEmpty()) {
                try {
                    val enriched = parseMobileVideos(client.get("media/catalog/library"))
                    videos = mergeMobileCatalog(basic, enriched)
                    if (videos.none { it.metadata != null }) catalogNotice = "Aucun film identifié pour le moment. La lecture reste disponible ; la gestion des jaquettes se trouve dans le menu ⋯."
                } catch (e: CancellationException) { throw e } catch (_: Exception) {
                    catalogNotice = "Les jaquettes sont indisponibles pour ce compte ou ce serveur. Vos vidéos restent accessibles."
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            message = e.message ?: "Impossible de charger la vidéothèque. Vérifiez la connexion au serveur."
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }
    BackHandler(enabled = selectedPath != null) { selectedPath = null }
    val selected = videos.find { it.path == selectedPath }
    if (selectedPath != null) {
        if (selected != null) MobileVideoDetails(client, selected, generation, { selectedPath = null }, { play(selected.path) })
        else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = { selectedPath = null }) { Text("‹  Vidéothèque") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else EmptyCard("Vidéo indisponible", "Ce fichier n’est plus présent dans la bibliothèque chargée.", MaisonIcons.PlayCircle)
            if (message.isNotBlank()) Notice(message, isError = true)
        }
        return
    }

    val visible = remember(videos, query, filter) { visibleMobileVideos(videos, query, filter == "À reprendre") }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp), state = gridState, modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Votre vidéothèque", style = MaterialTheme.typography.titleLarge)
                    Text("Films et vidéos · ordre alphabétique", style = MaterialTheme.typography.bodySmall, color = MaisonMuted)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(MaisonIcons.MoreHoriz, contentDescription = "Options de la médiathèque") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Jaquettes et identification des films") }, onClick = { menu = false; onCatalog() })
                    }
                }
            }
        }
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(query, { query = it }, label = { Text("Rechercher un titre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Effacer") } })
        }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) { Filters(listOf("Tous", "À reprendre"), filter) { filter = it } }
        item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${visible.size} vidéo${if (visible.size > 1) "s" else ""} · A–Z", color = MaisonMuted, style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !loading, onClick = { scope.launch { refresh() } }) { Text("Actualiser") }
            }
        }
        if (loading) item(key = "loading", span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (message.isNotBlank()) item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
            Column { Notice(message, isError = true); TextButton(enabled = !loading, onClick = { scope.launch { refresh() } }) { Text("Réessayer") } }
        }
        if (catalogNotice.isNotBlank()) item(key = "catalog-notice", span = { GridItemSpan(maxLineSpan) }) { Notice(catalogNotice) }
        if (visible.isEmpty() && !loading && message.isBlank()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            EmptyCard(if (query.isNotBlank()) "Aucun résultat" else if (filter == "À reprendre") "Aucune lecture à reprendre" else "Votre vidéothèque est vide",
                if (query.isNotBlank()) "Essayez un autre titre ou le nom du fichier." else if (filter == "À reprendre") "Les vidéos commencées apparaîtront ici." else "Les vidéos présentes sur votre serveur apparaîtront ici.", MaisonIcons.PlayCircle)
        }
        items(visible, key = { "video:${it.path}" }) { video ->
            MobilePosterCard(client, video, generation, { selectedPath = video.path }, { play(video.path) })
        }
        if (videos.any { it.metadata != null }) item(key = "credits", span = { GridItemSpan(maxLineSpan) }) { TmdbAttribution(R.drawable.tmdb_logo) }
    }
}

@Composable private fun MobilePosterCard(client: MaisonClient, video: MobileLibraryVideo, generation: Int, details: () -> Unit, play: () -> Unit) {
    val artwork = mobileArtwork(client, video, false, generation)
    Card(onClick = details, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Brush.verticalGradient(listOf(Color(0xFF302745), Color(0xFF181B28))))) {
            if (artwork != null) Image(artwork.asImageBitmap(), contentDescription = "Affiche de ${video.displayTitle}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(MaisonIcons.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Text(video.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(65.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
            FilledIconButton(onClick = play, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp)) {
                Icon(MaisonIcons.PlayCircle, contentDescription = "${if (video.hasProgress) "Reprendre" else "Lire"} ${video.displayTitle}", modifier = Modifier.size(22.dp))
            }
            video.progressFraction?.let { progress -> if (video.hasProgress) LinearProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(video.displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(video.metadata?.year?.takeIf { it.isNotBlank() }, if (video.hasProgress) "Reprise ${(video.position / 60).toInt()} min" else bytes(video.size)).joinToString(" · "), color = MaisonMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun MobileVideoDetails(client: MaisonClient, video: MobileLibraryVideo, generation: Int, back: () -> Unit, play: () -> Unit) {
    val meta = video.metadata
    val wide = mobileArtwork(client, video, true, generation)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TextButton(onClick = back) { Icon(MaisonIcons.Back, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Vidéothèque") } }
        if (wide != null) item { Card { Image(wide.asImageBitmap(), contentDescription = "Illustration de ${video.displayTitle}", modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(video.displayTitle, style = MaterialTheme.typography.headlineSmall)
                val facts = listOfNotNull(meta?.year?.takeIf { it.isNotBlank() }, meta?.runtimeMinutes?.takeIf { it > 0 }?.let { "$it min" }, meta?.genres?.joinToString(" · ")?.takeIf { it.isNotBlank() })
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = MaisonMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Button(onClick = play, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(MaisonIcons.PlayCircle, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(if (video.hasProgress) "Reprendre à ${(video.position / 60).toInt()} min" else "Regarder") } }
        if (video.hasProgress) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                video.progressFraction?.let { fraction -> LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()) }
                Text("Position de lecture enregistrée : ${(video.position / 60).toInt()} min", color = MaisonMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionHeading("Résumé"); Text(meta?.overview?.takeIf { it.isNotBlank() } ?: "Aucun résumé disponible pour cette vidéo. La lecture du fichier reste possible.", color = MaisonMuted) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Fichier sur votre serveur", style = MaterialTheme.typography.titleSmall); Text(video.path, color = MaisonMuted, style = MaterialTheme.typography.bodySmall); Text(bytes(video.size), color = MaisonMuted, style = MaterialTheme.typography.bodySmall) } } }
        if (meta != null) item { TmdbAttribution(R.drawable.tmdb_logo) }
    }
}
