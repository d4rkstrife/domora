package fr.domora.tv

import android.os.Bundle
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import fr.mamaison.app.*
import kotlinx.coroutines.*
import org.json.*
import java.net.NetworkInterface
import java.security.SecureRandom

class MainActivity:ComponentActivity(){
 private val receiver=PairingReceiver()
 override fun onDestroy(){receiver.close();super.onDestroy()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).apply{hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE};setContent{
  MaterialTheme(colorScheme=darkColorScheme(primary=Color.White,onPrimary=Color.Black,secondary=Color(0xffc5c5c8),background=Color(0xff0b0b0d),surface=Color(0xff18181b),onSurface=Color.White,onBackground=Color.White)){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Television(receiver)}}
 }}
}

@Composable fun Television(receiver:PairingReceiver){
 val context=androidx.compose.ui.platform.LocalContext.current
 val client=remember{MaisonClient(context)};val wg=remember{MaisonWireGuard.get(context)};val scope=rememberCoroutineScope()
 var status by remember{mutableStateOf("")};var qr by remember{mutableStateOf<Bitmap?>(null)}
 var videos by remember{mutableStateOf<List<JSONObject>>(emptyList())};var selected by remember{mutableStateOf<JSONObject?>(null)}
 var busy by remember{mutableStateOf(false)};var generation by remember{mutableIntStateOf(0)}
 var credits by remember{mutableStateOf(false)}
 fun load(){scope.launch{busy=true;status="Connexion au serveur…";try{client.usePrivateTunnel();client.authenticate();val data=JSONArray(client.get("tv/library"));videos=(0 until data.length()).map{data.getJSONObject(it)};status=if(videos.isEmpty())"Aucune vidéo dans les dossiers autorisés." else "${videos.size} vidéos disponibles"}catch(e:Exception){status=e.message?:"Connexion impossible"}finally{busy=false}}}
 val consent=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){if(wg.permissionIntent()==null)load()else{status="Autorisez le tunnel privé pour accéder aux vidéos.";busy=false}}
 fun connect(){val intent=wg.permissionIntent();if(intent!=null)consent.launch(intent)else load()}
 LaunchedEffect(generation){
  if(client.paired){connect();return@LaunchedEffect}
  busy=true
  try{
   val host=NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}.map{it.hostAddress.orEmpty()}.firstOrNull{it.startsWith("192.168.")||it.startsWith("10.")||Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..+").matches(it)}?:throw Exception("Connectez la télévision au même Wi-Fi que le téléphone.")
   val secret=Base64.encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)},Base64.NO_WRAP)
   val req=JSONObject().put("schema",1).put("name","Domora TV · ${android.os.Build.MODEL}").put("host",host).put("expires",System.currentTimeMillis()+600000).put("secret",secret).put("publicKey",client.publicIdentity()).put("wireguardKey",wg.publicKey())
   val text="domora-tv://pair?request="+Base64.encodeToString(req.toString().toByteArray(),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
   val matrix=MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,520,520)
   qr=Bitmap.createBitmap(520,520,Bitmap.Config.ARGB_8888).apply{setPixels(IntArray(520*520){if(matrix[it%520,it/520])android.graphics.Color.BLACK else android.graphics.Color.WHITE},0,520,0,0,520,520)}
   status="Dans Ma Maison : Plus → Téléviseurs → Scanner le QR code."
   client.acceptTvApproval(receiver.await(req));qr=null;connect()
  }catch(e:CancellationException){throw e}catch(e:Exception){status=e.message?:"Appairage impossible"}finally{busy=false}
 }
 selected?.let{video -> Video(client,video){selected=null};return}
 LaunchedEffect(videos.any{it.optString("catalogState")=="pending"}){if(videos.any{it.optString("catalogState")=="pending"})repeat(10){delay(3000);try{val data=JSONArray(client.get("tv/library"));videos=(0 until data.length()).map{data.getJSONObject(it)}}catch(_:Exception){return@LaunchedEffect}}}
 if(credits)AlertDialog(onDismissRequest={credits=false},title={Text("À propos de Domora TV")},text={TmdbAttribution(R.drawable.tmdb_logo)},confirmButton={TextButton(onClick={credits=false}){Text("Fermer")}})
 if(qr==null&&client.paired){CinemaHome(client,videos,status,busy,{connect()},{credits=true}){selected=it};return}
 Column(Modifier.fillMaxSize().padding(horizontal=40.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("DOMORA",style=MaterialTheme.typography.headlineSmall);Text(status,style=MaterialTheme.typography.bodySmall,color=Color(0xffa5a5aa),modifier=Modifier.widthIn(max=450.dp))};Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onClick={credits=true}){Text("À propos")};OutlinedButton(onClick={if(client.paired)connect()else{receiver.close();generation++}},enabled=!busy){Text(if(client.paired)"Actualiser" else "Nouveau QR code")}}}
  if(qr!=null){Row(horizontalArrangement=Arrangement.spacedBy(32.dp)){Image(qr!!.asImageBitmap(),"QR code d’autorisation",Modifier.size(310.dp));Column(Modifier.width(350.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){Text("Votre vidéothèque sur grand écran",style=MaterialTheme.typography.headlineMedium);Text("Autorisez cette télévision depuis votre téléphone. Vous choisissez les dossiers partagés et pouvez retirer l’accès à tout moment.\n\nTéléphone et télévision sur le même Wi-Fi pour cette première étape. Le serveur peut être à distance.\n\nCe QR code est valable 10 minutes.")}}}
  else if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  else {if(videos.any{it.optString("catalogState")=="unconfigured"})Text("Jaquettes automatiques : connecter TMDB depuis Ma Maison → Plus → Téléviseurs → Jaquettes.",color=Color(0xffa5a5aa),style=MaterialTheme.typography.bodySmall);MovieGrid(client,videos){selected=it}}
 }
}

@Composable fun Video(client:MaisonClient,video:JSONObject,close:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current;val scope=rememberCoroutineScope();var error by remember{mutableStateOf("")}
 val player=remember(video){ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(client.mediaHttpFactory())).build().apply{setMediaItem(MediaItem.fromUri(client.mediaUrl(video.getString("path"))));seekTo((video.optJSONObject("progress")?.optDouble("position",0.0)?.times(1000)?:0.0).toLong());addListener(object:Player.Listener{override fun onPlayerError(e:PlaybackException){error="Lecture impossible : ${e.errorCodeName}. Ce format peut être incompatible avec la télévision."}});prepare();playWhenReady=true}}
 suspend fun save(){if(player.duration>0)runCatching{client.post("tv/progress",JSONObject().put("path",video.getString("path")).put("position",player.currentPosition/1000.0).put("duration",player.duration/1000.0))}}
 val playerView=remember(player){PlayerView(context).apply{
  this.player=player;useController=true;controllerShowTimeoutMs=3000;setShowSubtitleButton(true)
  setBackgroundColor(android.graphics.Color.BLACK);setShutterBackgroundColor(android.graphics.Color.BLACK)
  resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
 }}
 BackHandler{scope.launch{save();close()}}
 LaunchedEffect(player){while(true){delay(10000);save()}}
 DisposableEffect(player){
  fun updateScreen(){playerView.keepScreenOn=player.playWhenReady&&player.playerError==null&&(player.playbackState==Player.STATE_READY||player.playbackState==Player.STATE_BUFFERING)}
  val screenListener=object:Player.Listener{override fun onEvents(player:Player,events:Player.Events){updateScreen()}}
  player.addListener(screenListener);updateScreen()
  val lifecycle=(context as ComponentActivity).lifecycle
  val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){player.pause();scope.launch{save()}}}
  lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);player.removeListener(screenListener);playerView.keepScreenOn=false;playerView.player=null;player.release()}
 }
 Box(Modifier.fillMaxSize().background(Color.Black)){
  AndroidView(factory={playerView.apply{requestFocus()}},modifier=Modifier.fillMaxSize())
  if(error.isNotEmpty())Column(Modifier.padding(40.dp)){Text(error);Button(onClick=close){Text("Retour aux vidéos")}}
 }
}
