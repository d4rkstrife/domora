package fr.mamaison.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.home.*
import com.google.home.google.GoogleDisplayDevice
import com.google.home.matter.standard.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Account credentials stay in the official Google SDK, never on the Pi. */
object GoogleHomeConnection {
    private var instance: HomeClient? = null
    @Synchronized fun client(context: Context): HomeClient = instance ?: Home.getClient(context.applicationContext,
        homeConfig = HomeConfig(coroutineContext = Dispatchers.IO, factoryRegistry = FactoryRegistry(
            traits = listOf(OnOff, LevelControl, ColorControl),
            types = listOf(OnOffLightDevice, DimmableLightDevice, ColorTemperatureLightDevice,
                ExtendedColorLightDevice, OnOffPluginUnitDevice, SpeakerDevice, GoogleDisplayDevice)
        ))).also { instance = it }
    fun register(activity: ComponentActivity) { client(activity).registerActivityResultCallerForPermissions(activity) }
}

@Composable fun GoogleHomeScreen(home: HomeClient) {
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(emptyList<HomeDevice>()) }
    var error by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(home) {
        try { home.hasPermissions().collect { granted = it == PermissionsState.GRANTED } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Google Home indisponible" }
    }
    LaunchedEffect(home, granted) {
        devices = emptyList()
        if (granted) try { home.devices().collect { devices = it.toList().sortedBy { d -> d.name } } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Appareils indisponibles" }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatusCard("Google Home", if (granted) "Compte autorisé" else "Connectez votre maison Google", MaisonIcons.Link) }
        item { Text("Retrouvez vos appareils Calex et les appareils partagés par Google Home. Les commandes dépendent des fonctions exposées par chaque appareil.", color = MaisonMuted) }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        item { Button(enabled = !pending, modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
            pending = true; error = ""
            try { val result = home.requestPermissions(ForcePermissionFlow.FORCE_LAUNCH)
                if (result.status != PermissionsResultStatus.SUCCESS) error = if (result.status == PermissionsResultStatus.CANCELLED) "Connexion annulée. Vous pouvez réessayer." else "Autorisation Google refusée : ${result.errorMessage ?: result.status}"
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Connexion Google : ${e.message ?: e.javaClass.simpleName}" }
            finally { pending = false }
        } }) { Text(if (pending) "Connexion…" else if (granted) "Gérer les autorisations Google" else "Connecter Google Home") } }
        if (granted && devices.isEmpty()) item { Text("Aucun appareil partagé. Sélectionnez la maison contenant votre ampoule dans les autorisations Google.", color = MaisonMuted) }
        items(devices, key = { it.id.toString() }) { GoogleDeviceCard(it) }
        item { Text("Les alarmes et l’écran du Nest Hub ne sont pas pilotés ici. Les scènes du Pi restent distinctes des commandes Google exécutées depuis ce téléphone.", color = MaisonMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable private fun GoogleDeviceCard(device: HomeDevice) {
    val scope = rememberCoroutineScope()
    var types by remember(device.id) { mutableStateOf(emptyList<DeviceType>()) }
    var error by remember(device.id) { mutableStateOf("") }
    var pending by remember(device.id) { mutableStateOf(false) }
    LaunchedEffect(device) {
        try { device.types().flatMapLatest { initial ->
            if (initial.isEmpty()) flowOf(emptyList())
            else combine(initial.map { type -> device.type(type.factory) }) { updated -> updated.toList() }
        }.collect { types = it } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "État indisponible" }
    }
    val light = types.any { it is OnOffLightDevice || it is DimmableLightDevice || it is ColorTemperatureLightDevice || it is ExtendedColorLightDevice }
    val traits = types.flatMap { it.traits() }
    val power = traits.filterIsInstance<OnOff>().firstOrNull()
    val level = traits.filterIsInstance<LevelControl>().firstOrNull()
    val color = traits.filterIsInstance<ColorControl>().firstOrNull()
    val online = types.any { it.metadata.sourceConnectivity.connectivityState in listOf(ConnectivityState.ONLINE, ConnectivityState.PARTIALLY_ONLINE) }
    fun command(block: suspend () -> Unit) { scope.launch {
        pending = true; error = ""
        try { withTimeout(15000) { block() } }
        catch (e: CancellationException) { if (e is TimeoutCancellationException) error = "L’appareil n’a pas répondu." else throw e }
        catch (e: Exception) { error = e.message ?: "Commande refusée par Google Home" }
        finally { pending = false }
    } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(device.name, style = MaterialTheme.typography.titleMedium); Text(if (online) "Google Home · En ligne" else "Google Home · État indisponible", color = if (online) MaisonGreen else MaisonMuted) }
            if (power != null) Switch(checked = power.onOff == true, enabled = online && !pending, onCheckedChange = { on -> command { if (on) power.on() else power.off() } })
        }
        if (light && level?.currentLevel != null) {
            var brightness by remember(device.id, level.currentLevel) { mutableFloatStateOf(level.currentLevel!!.toFloat() / 254f) }
            Text("Luminosité · ${(brightness * 100).toInt()} %")
            Slider(value = brightness, onValueChange = { brightness = it }, enabled = online && !pending, onValueChangeFinished = {
                command { level.moveToLevelWithOnOff((brightness * 254).toInt().toUByte(), null, LevelControlTrait.OptionsBitmap(), LevelControlTrait.OptionsBitmap()) }
            })
        }
        if (light && color != null && color.currentHue != null && color.currentSaturation != null) {
            Text("Couleur", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Rouge" to 0, "Vert" to 85, "Bleu" to 170).forEach { (name, hue) ->
                    OutlinedButton(enabled = online && !pending, onClick = { command { color.moveToHueAndSaturation(hue.toUByte(), 254.toUByte(), 0.toUShort(), ColorControlTrait.OptionsBitmap(), ColorControlTrait.OptionsBitmap()) } }) { Text(name) }
                }
            }
        }
        if (power == null && (!light || level == null)) Text("Aucune commande compatible exposée par Google pour cet appareil.", color = MaisonMuted)
        if (pending) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    } }
}


