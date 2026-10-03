package fr.mamaison.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject

@Composable fun FilesScreen(client:MaisonClient,folder:String,openFolder:(String)->Unit,play:(String)->Unit){
    val scope=rememberCoroutineScope();val context=LocalContext.current;var files by remember{mutableStateOf(listOf<JSONObject>())};var message by remember{mutableStateOf("")};var downloading by remember{mutableStateOf<JSONObject?>(null)};var selected by remember{mutableStateOf<JSONObject?>(null)};var menu by remember{mutableStateOf<String?>(null)};var operation by remember{mutableStateOf("")};var destination by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    suspend fun refresh(){files=JSONArray(client.get("files?path=${java.net.URLEncoder.encode(folder,"UTF-8")}")).objects()}
    val download=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->val file=downloading;if(uri!=null&&file!=null)scope.launch{busy=true;try{withContext(Dispatchers.IO){val c=client.mediaConnection(client.mediaUrl(file.getString("path")));try{if(c.responseCode!=200)throw Exception("Téléchargement impossible");c.inputStream.use{input->context.contentResolver.openOutputStream(uri)!!.use{output->input.copyTo(output,65536)}}}finally{c.disconnect()}};message="Fichier enregistré sur le téléphone."}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Téléchargement impossible"}finally{busy=false}}}
    LaunchedEffect(folder){busy=true;message="";try{refresh()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Fichiers indisponibles"}finally{busy=false}}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{Text(folder.ifEmpty{"Dossiers partagés"},style=MaterialTheme.typography.titleMedium);if(folder.isNotEmpty())TextButton(onClick={openFolder(folder.substringBeforeLast('/',""))}){Text("‹  Dossier parent")};Text("${files.size} élément(s)",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(message.isNotBlank())item{Notice(message)}
        if(files.isEmpty()&&!busy)item{EmptyCard("Ce dossier est vide","Les fichiers déposés sur le serveur apparaîtront ici.",MaisonIcons.Folder)}
        items(files,key={it.getString("path")}){file->
            val directory=file.getBoolean("directory");val video=file.getString("name").substringAfterLast('.').lowercase() in listOf("mp4","mkv","webm","avi","mov","m4v")
            Card(onClick={if(directory)openFolder(file.getString("path"))else if(video)play(file.getString("path"))else menu=file.getString("path")},modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(directory)MaisonIcons.Folder else if(video)MaisonIcons.PlayCircle else MaisonIcons.Storage,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(file.getString("name"),style=MaterialTheme.typography.titleMedium);Text(if(directory)"Dossier" else bytes(file.optDouble("size")),style=MaterialTheme.typography.bodySmall,color=MaisonMuted)}
                    Box{IconButton(enabled=!busy,onClick={menu=file.getString("path")}){Icon(MaisonIcons.MoreHoriz,contentDescription="Actions sur ${file.getString("name")}")};DropdownMenu(expanded=menu==file.getString("path"),onDismissRequest={menu=null}){
                        if(!directory)DropdownMenuItem(text={Text("Enregistrer sur le téléphone")},onClick={menu=null;downloading=file;download.launch(file.getString("name"))})
                        DropdownMenuItem(text={Text("Renommer")},onClick={menu=null;selected=file;operation="rename";destination=file.getString("name")})
                        DropdownMenuItem(text={Text("Déplacer")},onClick={menu=null;selected=file;operation="move";destination=file.getString("path")})
                        DropdownMenuItem(text={Text("Mettre à la corbeille")},onClick={menu=null;selected=file;operation="remove"})
                    }}
                }
            }
        }
    }
    if(selected!=null)AlertDialog(onDismissRequest={selected=null},title={Text(if(operation=="remove")"Mettre à la corbeille ?" else if(operation=="rename")"Renommer" else "Déplacer")},text={if(operation=="remove")Text("Le contenu sera conservé dans la corbeille du serveur.")else OutlinedTextField(destination,{destination=it},label={Text(if(operation=="rename")"Nouveau nom" else "Chemin dans les dossiers partagés")})},confirmButton={TextButton(enabled=!busy&&(operation=="remove"||destination.isNotBlank()),onClick={val file=selected!!;val currentOperation=operation;val currentDestination=destination;selected=null;scope.launch{busy=true;try{client.post("files/action",JSONObject().put("path",file.getString("path")).put("action",currentOperation).put("name",currentDestination).put("destination",currentDestination));refresh();message="Modification enregistrée."}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Opération impossible"}finally{busy=false}}}){Text("Confirmer")}},dismissButton={TextButton(onClick={selected=null}){Text("Annuler")}})
}
