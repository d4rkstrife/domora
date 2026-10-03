package fr.mamaison.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable fun CamerasScreen(client:MaisonClient,watch:(String,String)->Unit){
    val scope=rememberCoroutineScope();var cameras by remember{mutableStateOf(listOf<JSONObject>())};var found by remember{mutableStateOf(listOf<JSONObject>())};var chosen by remember{mutableStateOf(setOf<String>())};var adding by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var previewGeneration by remember{mutableIntStateOf(0)}
    suspend fun refresh(){cameras=JSONArray(client.get("cameras")).objects();previewGeneration++}
    fun action(work:suspend ()->Unit){scope.launch{busy=true;message="";try{work()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Caméra indisponible"}finally{busy=false}}}
    LaunchedEffect(Unit){action{refresh()}}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("${cameras.size} caméra${if(cameras.size>1)"s" else ""}",color=MaisonMuted);TextButton(enabled=!busy,onClick={action{refresh()}}){Text("Actualiser")}}}
        if(message.isNotBlank())item{Notice(message)}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        items(cameras,key={it.getString("id")}){camera->CameraPreview(client,camera,previewGeneration){watch(camera.getString("id"),camera.getString("name"))}}
        if(cameras.isEmpty()&&!busy)item{EmptyCard("Votre première caméra","Branchez une webcam USB au Pi, puis lancez la recherche.",MaisonIcons.Videocam)}
        item{OutlinedButton(enabled=!busy,onClick={adding=true;chosen=emptySet();found=emptyList();action{val result=JSONObject(client.post("cameras/discover"));val known=cameras.map{it.optString("sourceId")}.toSet();found=result.getJSONArray("cameras").objects().filter{it.optString("sourceId") !in known};message=result.optString("message")}},modifier=Modifier.fillMaxWidth()){Text("+ Ajouter une caméra")}}
    }
    if(adding)AlertDialog(onDismissRequest={if(!busy)adding=false},title={Text("Caméras USB détectées")},text={LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(!busy&&found.isEmpty())"Aucune nouvelle caméra. Vérifiez le branchement USB." else "Choisissez les webcams à ajouter.",color=MaisonMuted);if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)};items(found){camera->Row(verticalAlignment=Alignment.CenterVertically){val id=camera.getString("sourceId");Checkbox(id in chosen,enabled=!busy,onCheckedChange={checked->chosen=if(checked)chosen+id else chosen-id});Text(camera.getString("name"))}}}},confirmButton={TextButton(enabled=!busy&&chosen.isNotEmpty(),onClick={action{chosen.forEach{client.post("cameras",JSONObject().put("sourceId",it))};refresh();adding=false;message="Caméras ajoutées."}}){Text("Ajouter")}},dismissButton={TextButton(enabled=!busy,onClick={adding=false}){Text("Fermer")}})
}
@Composable private fun CameraPreview(client:MaisonClient,camera:JSONObject,generation:Int,watch:()->Unit){
    var frame by remember(camera.getString("id")){mutableStateOf<android.graphics.Bitmap?>(null)};var failed by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(false)}
    LaunchedEffect(camera.getString("id"),generation){if(camera.optBoolean("online")){loading=true;failed=false;try{frame=client.cameraFrame(camera.getString("id"))}catch(e:CancellationException){throw e}catch(_:Exception){failed=true}finally{loading=false}}else frame=null}
    Card(onClick=watch,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(camera.getString("name"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text(if(camera.optBoolean("online"))"● Connectée" else "Déconnectée",color=if(camera.optBoolean("online"))MaisonGreen else MaisonMuted,style=MaterialTheme.typography.labelSmall)}
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color(0xFF0C1823)),contentAlignment=Alignment.Center){
            if(frame!=null)Image(frame!!.asImageBitmap(),contentDescription="Aperçu de ${camera.getString("name")}",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            else if(loading)CircularProgressIndicator(Modifier.size(28.dp))else Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(MaisonIcons.Videocam,null,tint=MaisonMuted,modifier=Modifier.size(40.dp));Text(if(failed)"Aperçu indisponible" else "Aucun aperçu",color=MaisonMuted)}
            Surface(color=Color.Black.copy(alpha=.55f),shape=MaterialTheme.shapes.small,modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp)){Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(MaisonIcons.PlayCircle,null,tint=Color.White,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Ouvrir le direct",color=Color.White,style=MaterialTheme.typography.labelMedium)}}
        }
    }
}
