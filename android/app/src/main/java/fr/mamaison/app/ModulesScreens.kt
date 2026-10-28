package fr.mamaison.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.objects()=(0 until length()).map{getJSONObject(it)}
internal fun bytes(value:Double)=if(value>=1e9)"%.1f Go".format(value/1e9)else "%.1f Mo".format(value/1e6)
@Composable internal fun Notice(text:String,isError:Boolean=false){if(text.isNotBlank())Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(isError)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)){Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(MaisonIcons.Info,null,tint=if(isError)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp));Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)}}}
@Composable internal fun Filters(options:List<String>,selected:String,choose:(String)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){options.forEach{FilterChip(selected=it==selected,onClick={choose(it)},label={Text(it)})}}}
@Composable internal fun EmptyCard(title:String,description:String,icon:androidx.compose.ui.graphics.vector.ImageVector=MaisonIcons.Info){StatusCard(title,description,icon)}

@Composable fun DownloadsScreen(client:MaisonClient,initialMagnet:String="",onSharedMagnetConsumed:()->Unit={},chooseTorrent:()->Unit){
    val scope=rememberCoroutineScope();var list by remember{mutableStateOf(listOf<JSONObject>())};var magnet by rememberSaveable{mutableStateOf(initialMagnet)}
    var message by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};var pending by remember{mutableStateOf(false)}
    var filter by rememberSaveable{mutableStateOf("Tous")};var add by rememberSaveable{mutableStateOf(initialMagnet.isNotBlank())};var remove by remember{mutableStateOf<JSONObject?>(null)};var deleteData by remember{mutableStateOf(false)}
    suspend fun refresh(){list=JSONArray(client.get("downloads")).objects()}
    fun action(work:suspend ()->Unit){scope.launch{pending=true;message="";try{work();refresh()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Action impossible"}finally{pending=false}}}
    LaunchedEffect(initialMagnet){if(initialMagnet.isNotBlank()){magnet=initialMagnet;add=true;onSharedMagnetConsumed()}}
    LaunchedEffect(Unit){while(true){try{refresh()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Téléchargements indisponibles"}finally{loading=false};delay(4000)}}
    val visible=list.filter{when(filter){"En cours"->it.optDouble("progress")<1&&it.optString("state")!="pause";"Terminés"->it.optDouble("progress")>=1;"En pause"->it.optString("state")=="pause";else->true}}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{StatusCard("${list.size} téléchargement${if(list.size>1)"s" else ""}","↓ ${bytes(list.sumOf{it.optDouble("downloadSpeed")})}/s    ↑ ${bytes(list.sumOf{it.optDouble("uploadSpeed")})}/s",MaisonIcons.Storage)}
        item{Button(onClick={add=true},modifier=Modifier.fillMaxWidth()){Text("+ Ajouter un téléchargement")}}
        item{Filters(listOf("Tous","En cours","Terminés","En pause"),filter){filter=it}}
        if(loading||pending)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(message.isNotBlank())item{Notice(message)}
        if(visible.isEmpty()&&!loading&&message.isBlank())item{EmptyCard("Aucun téléchargement","Ajoutez un lien magnet ou un fichier torrent, ou choisissez un autre filtre.",MaisonIcons.Storage)}
        items(visible,key={it.getString("id")}){torrent->
            val progress=torrent.optDouble("progress").toFloat().coerceIn(0f,1f)
            val state=when(torrent.optString("state")){"pause"->"En pause";"telechargement","téléchargement"->"Téléchargement";"partage"->"Partage";"verification","vérification"->"Vérification";"erreur"->"Action requise";"en_attente"->"En attente";else->if(progress>=1f)"Terminé" else "En cours"}
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Icon(MaisonIcons.Folder,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(torrent.getString("name"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)}
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${(progress*100).toInt()} % · ${bytes(torrent.optDouble("size"))}",color=MaisonMuted,style=MaterialTheme.typography.bodySmall);Text(state,color=if(progress>=1f)MaisonGreen else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)}
                Text("↓ ${bytes(torrent.optDouble("downloadSpeed"))}/s   ↑ ${bytes(torrent.optDouble("uploadSpeed"))}/s",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)
                if(!torrent.isNull("error"))Text(torrent.optString("error"),color=if(torrent.optString("issueType")=="local")MaterialTheme.colorScheme.error else MaisonMuted,style=MaterialTheme.typography.bodySmall)
                Row{TextButton(enabled=!pending,onClick={action{client.post("downloads/${torrent.getString("id")}/action",JSONObject().put("action",if(torrent.optString("state")=="pause")"resume" else "pause"))}}){Text(if(torrent.optString("state")=="pause")"Reprendre" else "Mettre en pause")};TextButton(enabled=!pending,onClick={remove=torrent;deleteData=false}){Text("Retirer",color=MaterialTheme.colorScheme.error)}}
            }}
        }
    }
    if(add)AlertDialog(onDismissRequest={if(!pending)add=false},title={Text("Nouveau téléchargement")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Collez un lien magnet ou choisissez un fichier .torrent.",color=MaisonMuted);OutlinedTextField(magnet,{magnet=it},label={Text("Lien magnet")},modifier=Modifier.fillMaxWidth());OutlinedButton(enabled=!pending,onClick={add=false;chooseTorrent()},modifier=Modifier.fillMaxWidth()){Text("Choisir un fichier torrent")};if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(enabled=!pending&&magnet.trim().startsWith("magnet:?"),onClick={action{client.post("downloads",JSONObject().put("magnet",magnet.trim()));magnet="";add=false}}){Text("Ajouter")}},dismissButton={TextButton(enabled=!pending,onClick={add=false}){Text("Annuler")}})
    if(remove!=null)AlertDialog(onDismissRequest={remove=null},title={Text("Retirer ce téléchargement ?")},text={Column{Text(remove!!.getString("name"));Row(verticalAlignment=Alignment.CenterVertically){Checkbox(deleteData,{deleteData=it});Text("Supprimer aussi les fichiers")}}},confirmButton={TextButton(onClick={val target=remove!!;remove=null;action{client.post("downloads/${target.getString("id")}/action",JSONObject().put("action","remove").put("deleteData",deleteData).put("confirm",if(deleteData)"SUPPRIMER" else ""))}}){Text("Confirmer",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={remove=null}){Text("Annuler")}})
}
