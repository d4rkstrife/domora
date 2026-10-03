package fr.mamaison.app

import android.os.Bundle
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.util.Base64
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var client: MaisonClient
    private var googleHome: com.google.home.HomeClient? = null
    private var googleHomeStartupError = ""
    private var discovery: NsdManager.DiscoveryListener? = null
    private val found = mutableStateListOf<String>()
    private var incomingMagnet by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); client = MaisonClient(this);incomingMagnet=intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf{it.startsWith("magnet:?")}?:""
        try {
            GoogleHomeConnection.register(this)
            googleHome = GoogleHomeConnection.client(this)
        } catch (e: Exception) {
            googleHomeStartupError = "${e.javaClass.simpleName} : ${e.message ?: "Initialisation Google impossible"}"
            android.util.Log.e("MaMaisonGoogleHome", "Google Home initialization failed", e)
        } catch (e: LinkageError) {
            googleHomeStartupError = "${e.javaClass.simpleName} : ${e.message ?: "Bibliothèque Google indisponible"}"
            android.util.Log.e("MaMaisonGoogleHome", "Google Home linkage failed", e)
        }
        setContent { MaisonTheme { Maison() } }
    }
    @Suppress("DEPRECATION")
    private fun discover() {
        if (discovery != null) return
        val nsd = getSystemService(NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) { discovery = null }
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, error: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) { val host = info.host.hostAddress ?: return; val address = "http://${if (host.contains(':')) "[$host]" else host}:${info.port}"; runOnUiThread { if (!found.contains(address)) found.add(address) } }
                })
            }
        }; discovery = listener; nsd.discoverServices("_mamaison._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    }
    override fun onDestroy() { discovery?.let { (getSystemService(NSD_SERVICE) as NsdManager).stopServiceDiscovery(it) }; super.onDestroy() }
    @Composable private fun Maison() {
        val scope = rememberCoroutineScope(); var connected by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
        var address by remember { mutableStateOf(client.address) }; var code by remember { mutableStateOf("") }; var tab by rememberSaveable { mutableStateOf(if(incomingMagnet.isNotBlank())"Téléchargements" else "Accueil") }
        var stats by remember { mutableStateOf(JSONObject()) }; var files by remember { mutableStateOf(listOf<JSONObject>()) }; var folder by rememberSaveable { mutableStateOf("") }; var playing by rememberSaveable { mutableStateOf<String?>(null) }
        var connectionStatus by remember { mutableStateOf("") }; var watchingCamera by rememberSaveable { mutableStateOf<String?>(null) }; var cameraName by rememberSaveable { mutableStateOf("Caméra") }
        var sectionParent by rememberSaveable { mutableStateOf("Plus") }
        fun navigateSection(target:String){sectionParent=tab;tab=target}
        fun explainConnection(e:Exception):String = when(e){
            is java.net.SocketTimeoutException -> "Le serveur ne répond pas à $address. Vérifiez que le téléphone est sur le Wi-Fi de la maison."
            is java.net.ConnectException -> "Connexion refusée à $address. Vérifiez l’adresse et que le service Ma Maison est démarré."
            is java.net.UnknownHostException -> "Adresse du serveur introuvable : $address."
            else -> e.message?.takeIf{it.isNotBlank()} ?: "Connexion impossible (${e.javaClass.simpleName})."
        }
        val torrentPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{try{val bytes=withContext(kotlinx.coroutines.Dispatchers.IO){contentResolver.openInputStream(uri)!!.use{stream->val out=java.io.ByteArrayOutputStream();val block=ByteArray(8192);while(true){val n=stream.read(block);if(n<0)break;if(out.size()+n>2000000)throw Exception("Le fichier torrent est trop volumineux");out.write(block,0,n)};out.toByteArray()}};client.post("downloads",JSONObject().put("torrent",Base64.encodeToString(bytes,Base64.NO_WRAP)));tab="Téléchargements"}catch(e:Exception){error=e.message?:"Ajout impossible"}}}
        val backupPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{try{val backup=client.get("backup");withContext(kotlinx.coroutines.Dispatchers.IO){contentResolver.openOutputStream(uri)!!.use{it.write(backup.toByteArray())}}}catch(e:Exception){error=e.message?:"Sauvegarde impossible"}}}
        fun load() { scope.launch { busy = true; error = ""; try { stats = JSONObject(client.get("server")); val list = JSONArray(client.get("files?path=${java.net.URLEncoder.encode(folder, "UTF-8")}")); files = (0 until list.length()).map { list.getJSONObject(it) } } catch (e: Exception) { error = e.message ?: "Serveur inaccessible" }; busy = false } }
        LaunchedEffect(Unit) { if (client.paired) { busy = true; connectionStatus="Reconnexion au serveur…"; try { client.authenticate(); connected = true; load() } catch(e:CancellationException){throw e} catch (e: Exception) { error = explainConnection(e) } finally {busy = false;connectionStatus=""} } }
        LaunchedEffect(folder) { if (connected) load() }
        val tabs = listOf("Accueil", "Appareils", "Caméras", "Médias", "Plus")
        val icons = listOf(MaisonIcons.Home, MaisonIcons.Devices, MaisonIcons.Videocam, MaisonIcons.PlayCircle, MaisonIcons.MoreHoriz)
        if(connected&&playing!=null){Video(playing!!){playing=null};return}
        if(connected&&watchingCamera!=null){CameraLive(watchingCamera!!,cameraName){watchingCamera=null};return}
        androidx.activity.compose.BackHandler(enabled=connected&&tab !in tabs){tab=if(tab=="Seedbox")"Accueil" else sectionParent.takeIf{it in tabs||it=="Seedbox"}?:"Plus"}
        Scaffold(bottomBar = { if (connected) NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) { tabs.forEachIndexed { i, name -> NavigationBarItem(selected = tab == name || (tab !in tabs && name == (if(tab=="Seedbox"||sectionParent=="Seedbox")"Accueil" else sectionParent)), onClick = { tab = name; playing = null }, icon = { Icon(icons[i], contentDescription = name) }, label = { Text(name) }) } } }) { padding ->
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).then(if(!connected)Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal=20.dp,vertical=16.dp)) {
                Text(if (connected && tab != "Accueil") tab else "Ma Maison", style = MaterialTheme.typography.headlineMedium)
                Text(if (!connected) "Votre maison commence ici" else when(tab){"Accueil" -> "Bienvenue chez vous"; "Plus" -> "Paramètres et intégrations"; "Appareils" -> "Tous vos appareils connectés"; "Caméras" -> "Gardez un œil sur votre maison"; "Médias" -> "Vos films et vidéos"; "Seedbox" -> "Vos contenus sur votre serveur"; else -> "Votre maison, à portée de main"}, color=MaisonMuted,style=MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(20.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                if (!connected && connectionStatus.isNotEmpty()) Text(connectionStatus)
                if (!connected) {
                    Text("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",style=MaterialTheme.typography.bodySmall)
                    Text("Recherchez votre serveur sur le Wi-Fi de la maison, puis autorisez ce téléphone.")
                    Button(onClick = { discover() }, Modifier.fillMaxWidth()) { Text("Rechercher mon serveur") }
                    found.forEach { url -> OutlinedButton(onClick = { address = url }, Modifier.fillMaxWidth()) { Text("Serveur trouvé · $url") } }
                    OutlinedTextField(address, { address = it }, label = { Text("Adresse manuelle (secours)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(code, { code = it.filter{c->c.isDigit()}.take(6) }, label = { Text("Code d’appairage") }, modifier = Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedButton(enabled=!busy&&address.isNotBlank(),onClick={scope.launch{busy=true;error="";connectionStatus="Vérification du serveur…";try{client.address=address.trim().trimEnd('/');connectionStatus=client.checkServer()}catch(e:CancellationException){throw e}catch(e:Exception){connectionStatus="";error=explainConnection(e)}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text("Vérifier la connexion au serveur")}
                    if(client.paired)OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;error="";connectionStatus="Reconnexion…";try{client.authenticate();connected=true;load()}catch(e:CancellationException){throw e}catch(e:Exception){error=explainConnection(e)}finally{busy=false;connectionStatus=""}}},modifier=Modifier.fillMaxWidth()){Text("Réessayer avec mon appairage existant")}
                    Text("Le code est affiché dans le journal du serveur pendant dix minutes après son démarrage.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !busy && address.isNotBlank() && code.length == 6, onClick = { scope.launch { busy = true;error="";connectionStatus="Appairage en cours…"; try { client.address = address.trim().trimEnd('/'); client.pair(code); connected = true; load() } catch(e:CancellationException){throw e} catch (e: Exception) { error = explainConnection(e) } finally{busy = false;connectionStatus=""} } }, modifier = Modifier.fillMaxWidth()) { Text("Connecter ma maison") }
                    if(!busy&&code.length!=6)Text("Saisissez les 6 chiffres du code pour activer le bouton de connexion.",style=MaterialTheme.typography.bodySmall)
                } else when (tab) {
                    "Accueil" -> DashboardScreen(client,stats,{navigateSection(it)},{load()})
                    "Téléviseurs" -> TelevisionsScreen(client)
                    "Seedbox" -> SeedboxScreen(stats){navigateSection(it)}
                    "Médias" -> LibraryScreen(client){playing=it}
                    "Fichiers" -> FilesScreen(client,folder,{folder=it},{playing=it})
                    "Caméras" -> CamerasScreen(client){id,name->watchingCamera=id;cameraName=name}
                    "Google Home" -> {
                        val home = googleHome
                        if (home != null) GoogleHomeScreen(home)
                        else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            StatusCard("Google Home indisponible", "Les autres fonctions de Ma Maison restent accessibles.", MaisonIcons.Link)
                            Text(googleHomeStartupError, color=MaterialTheme.colorScheme.error)
                            Text("Vérifiez les mises à jour de Google Home et des services Google Play, puis relancez Ma Maison. Si le problème persiste, transmettez le message ci-dessus.", color=MaisonMuted)
                        }
                    }
                    "Appareils" -> Column { TextButton(onClick={navigateSection("Google Home")}){Text("Mes appareils Google Home")}; HomeScreen(client) }
                    "Plus" -> SettingsScreen(client,{navigateSection(it)},{backupPicker.launch("ma-maison-sauvegarde.json")})
                    "Téléchargements" -> DownloadsScreen(client,incomingMagnet){torrentPicker.launch(arrayOf("application/x-bittorrent","application/octet-stream"))}
                    "Pièces et scènes" -> RoomsScenesScreen(client)
                }
            }
        }
    }
    @Composable private fun Tile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal=20.dp,vertical=16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)); Spacer(Modifier.width(16.dp)); Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9FB6CC)) } } }
    }
    @Composable private fun CameraLive(id:String,name:String,onBack:()->Unit){
        var frame by remember(id){mutableStateOf<android.graphics.Bitmap?>(null)}
        var error by remember(id){mutableStateOf("")};var fullScreen by remember(id){mutableStateOf(false)};var controls by remember{mutableStateOf(true)}
        LaunchedEffect(id){while(true){try{frame=client.cameraFrame(id);error=""}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Caméra inaccessible";delay(2000)};delay(250)}}
        LaunchedEffect(fullScreen,controls){if(fullScreen&&controls){delay(3000);controls=false}}
        androidx.activity.compose.BackHandler{if(fullScreen)fullScreen=false else onBack()}
        if(fullScreen){
            DisposableEffect(Unit){
                val previousOrientation=requestedOrientation
                val bars=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
                val previousBehavior=bars.systemBarsBehavior
                val keep=window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                bars.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                onDispose{bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());bars.systemBarsBehavior=previousBehavior;androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,true);if(!keep)window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);requestedOrientation=previousOrientation}
            }
            Box(Modifier.fillMaxSize().background(Color.Black).clickable{controls=!controls},contentAlignment=androidx.compose.ui.Alignment.Center){
                if(frame!=null)Image(frame!!.asImageBitmap(),contentDescription="Direct webcam",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
                if(controls)TextButton(onClick={fullScreen=false},modifier=Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha=.6f))){Text("Quitter le plein écran",color=Color.White)}
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,modifier=Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(20.dp).background(Color.Black))
            }
        }else{
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                TextButton(onClick=onBack){Text("‹  Mes caméras")}
                Text(name,style=MaterialTheme.typography.headlineMedium)
                Text("Webcam USB · Sans son",color=MaisonMuted)
                Card(Modifier.fillMaxWidth()){Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black),contentAlignment=androidx.compose.ui.Alignment.Center){if(frame!=null)Image(frame!!.asImageBitmap(),contentDescription="Direct webcam",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())else if(error.isBlank())CircularProgressIndicator()else Icon(MaisonIcons.Videocam,null,tint=MaisonMuted,modifier=Modifier.size(42.dp))}}
                if(error.isNotBlank())Notice(error)
                Button(onClick={controls=true;fullScreen=true},modifier=Modifier.fillMaxWidth()){Text("Voir en plein écran")}
                Text("Le direct s’actualise automatiquement. Touchez l’écran en plein écran pour afficher les commandes.",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    @Composable private fun Video(file: String,onBack:()->Unit) {
        var fullScreen by remember(file){mutableStateOf(false)}
        var controlsVisible by remember(file){mutableStateOf(true)}
        var playbackError by remember(file){mutableStateOf("")}
        val player = remember(file) { ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(client.mediaHttpFactory())).build().apply { setMediaItem(MediaItem.fromUri(client.mediaUrl(file))); prepare(); playWhenReady = true } }
        LaunchedEffect(file){try{val progress=JSONObject(client.get("media/progress?path=${java.net.URLEncoder.encode(file,"UTF-8")}"));player.seekTo((progress.optDouble("position")*1000).toLong())}catch(_:Exception){};while(true){delay(5000);if(player.duration>0)try{client.post("media/progress",JSONObject().put("path",file).put("position",player.currentPosition/1000.0).put("duration",player.duration/1000.0))}catch(_:Exception){}}}
        DisposableEffect(player) {
            val listener=object:androidx.media3.common.Player.Listener{
                override fun onPlayerError(error:androidx.media3.common.PlaybackException){playbackError=when(error.errorCode){
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Ce format vidéo ou audio n’est pas pris en charge par ce téléphone. Le serveur transmet les fichiers sans conversion."
                    else -> "Lecture interrompue : ${error.errorCodeName}. Vérifiez la connexion au serveur puis réessayez."
                }}
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener);player.release() }
        }
        androidx.activity.compose.BackHandler{if(fullScreen)fullScreen=false else onBack()}
        if(!fullScreen){
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp,vertical=16.dp)){
                TextButton(onClick=onBack){Text("← Bibliothèque")}
                if(playbackError.isNotEmpty()){Text(playbackError,color=MaterialTheme.colorScheme.error);TextButton(onClick={playbackError="";player.prepare();player.play()}){Text("Réessayer la lecture")}}
                AndroidView(factory = { PlayerView(it).apply { this.player = player;setFullscreenButtonClickListener{fullScreen=it};setFullscreenButtonState(false) } }, modifier = Modifier.fillMaxWidth().weightOrHeight(),onReset=null,onRelease={it.player=null})
                TextButton(onClick={fullScreen=true}){Text("Plein écran")}
            }
        }else{
            DisposableEffect(Unit){
                val previousOrientation=requestedOrientation
                val bars=androidx.core.view.WindowCompat.getInsetsController(window,window.decorView)
                val previousBehavior=bars.systemBarsBehavior
                val wasKeepingScreenOn=window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                bars.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                onDispose{
                    bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    bars.systemBarsBehavior=previousBehavior
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,true)
                    if(!wasKeepingScreenOn)window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    requestedOrientation=previousOrientation
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black)){
                AndroidView(factory={PlayerView(it).apply{this.player=player;setFullscreenButtonClickListener{fullScreen=it};setFullscreenButtonState(true);setControllerVisibilityListener(PlayerView.ControllerVisibilityListener{visibility->controlsVisible=visibility==android.view.View.VISIBLE})}},modifier=Modifier.fillMaxSize(),onReset=null,onRelease={it.player=null})
                if(controlsVisible)TextButton(onClick={fullScreen=false},modifier=Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha=0.6f))){Text("Quitter le plein écran",color=Color.White)}
                if(playbackError.isNotEmpty())Text(playbackError,color=MaterialTheme.colorScheme.error,modifier=Modifier.align(androidx.compose.ui.Alignment.TopStart).fillMaxWidth(0.7f).padding(16.dp).background(Color.Black))
            }
        }
    }
    private fun Modifier.weightOrHeight() = this.height(300.dp)
}



