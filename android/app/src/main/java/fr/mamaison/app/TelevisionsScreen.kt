package fr.mamaison.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable fun TelevisionsScreen(client:MaisonClient) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var request by remember{mutableStateOf<JSONObject?>(null)}
    var folders by remember{mutableStateOf(emptyList<String>())}
    var selected by remember{mutableStateOf(setOf<String>())}
    var devices by remember{mutableStateOf(emptyList<JSONObject>())}
    var pending by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    var pasted by remember{mutableStateOf("")};var remove by remember{mutableStateOf<JSONObject?>(null)}
    suspend fun refresh(){devices=JSONArray(client.get("devices/authorized")).let{list->(0 until list.length()).map{list.getJSONObject(it)}.filter{it.optString("role")=="tv"}}}
    fun action(work:suspend()->Unit){scope.launch{pending=true;error="";try{work()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Action TV impossible"}finally{pending=false}}}
    fun loadRequest(text:String){action{val parsed=TvLink.parse(text);val entries=JSONArray(client.get("files"));folders=(0 until entries.length()).map{entries.getJSONObject(it)}.filter{it.optBoolean("directory")}.map{it.getString("path")};selected=emptySet();request=parsed}}
    LaunchedEffect(Unit){action{refresh()}}
    if(remove!=null)AlertDialog(onDismissRequest={remove=null},title={Text("Retirer cette télévision ?")},text={Text("Ses vidéos et sa connexion privée au serveur seront révoquées.")},confirmButton={TextButton(onClick={val id=remove!!.getString("id");remove=null;action{client.delete("devices/authorized/$id");refresh()}}){Text("Retirer l’accès")}},dismissButton={TextButton(onClick={remove=null}){Text("Annuler")}})
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{StatusCard("Domora TV","Approuvez une télé et ses dossiers vidéo",MaisonIcons.Videocam)}
        item{Text("Ouvrez Domora TV, puis scannez son QR code. Le téléphone et la télé doivent être sur le même Wi-Fi ; votre Pi peut être ailleurs grâce à WireGuard.",color=MaisonMuted)}
        if(pending)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
        if(request==null) {
            item{Button(enabled=!pending,modifier=Modifier.fillMaxWidth(),onClick={GmsBarcodeScanning.getClient(context).startScan().addOnSuccessListener{result->loadRequest(result.rawValue?:"")}.addOnFailureListener{error=it.message?:"Scanner indisponible"}}){Text("Scanner le QR de la télé")}}
            item{OutlinedTextField(pasted,{pasted=it},label={Text("Ou coller une demande Domora TV")},modifier=Modifier.fillMaxWidth());TextButton(enabled=!pending&&pasted.isNotBlank(),onClick={loadRequest(pasted)}){Text("Lire la demande")}}
        } else {
            item{Text(request!!.optString("name","Télévision"),style=MaterialTheme.typography.titleLarge);Text("Choisissez les dossiers autorisés. La télé pourra seulement lire les vidéos.",color=MaisonMuted)}
            items(folders){folder->Row{Checkbox(checked=folder in selected,onCheckedChange={checked->selected=if(checked)selected+folder else selected-folder});Text(folder,Modifier.padding(top=12.dp))}}
            item{Row{Checkbox(checked="" in selected,onCheckedChange={selected=if(it)setOf("")else emptySet()});Text("Toutes les vidéos du serveur",Modifier.padding(top=12.dp))}}
            item{Button(enabled=!pending&&selected.isNotEmpty(),modifier=Modifier.fillMaxWidth(),onClick={action{
                val source=request!!
                val data=JSONObject().put("name",source.optString("name","Domora TV")).put("publicKey",source.getString("publicKey")).put("wireguardKey",source.getString("wireguardKey")).put("roots",JSONArray(selected.toList()))
                val approval=JSONObject(client.post("tv/approve",data)).put("localAddress",client.address)
                try{TvLink.deliver(source,approval)}finally{refresh()};request=null;pasted="";error="Télévision autorisée. Acceptez la connexion VPN sur son écran."
            }}){Text("Autoriser cette télévision")}}
            item{TextButton(onClick={request=null}){Text("Annuler")}}
        }
        items(devices,key={it.getString("id")}){device->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(device.getString("name"),style=MaterialTheme.typography.titleMedium);val roots=device.optJSONArray("mediaRoots");val labels=if(roots==null)"" else (0 until roots.length()).map{roots.getString(it).ifEmpty{"Toutes les vidéos"}}.joinToString(", ");Text(if(device.optBoolean("revoked"))"Accès retiré" else "Lecture vidéo · $labels",color=MaisonMuted);if(!device.optBoolean("revoked"))TextButton(enabled=!pending,onClick={remove=device}){Text("Retirer l’accès",color=MaterialTheme.colorScheme.error)}}}}
    }
}
