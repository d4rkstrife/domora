package fr.mamaison.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable fun RoomsScenesScreen(client:MaisonClient){
    val scope=rememberCoroutineScope();var rooms by remember{mutableStateOf(listOf<JSONObject>())};var scenes by remember{mutableStateOf(listOf<JSONObject>())};var devices by remember{mutableStateOf(listOf<JSONObject>())}
    var tab by rememberSaveable{mutableStateOf("Pièces")};var selectedRoom by rememberSaveable{mutableStateOf<String?>(null)};var name by rememberSaveable{mutableStateOf("")};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var createRoom by remember{mutableStateOf(false)};var createScene by remember{mutableStateOf(false)};var step by remember{mutableStateOf(0)};var chosen by remember{mutableStateOf(setOf<String>())};var turnOn by remember{mutableStateOf(false)}
    suspend fun refresh(){rooms=JSONArray(client.get("rooms")).objects();scenes=JSONArray(client.get("scenes")).objects();devices=JSONArray(client.get("home/devices")).objects()}
    fun action(work:suspend ()->Unit){scope.launch{busy=true;message="";try{work();refresh()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Action impossible"}finally{busy=false}}}
    LaunchedEffect(Unit){action{}}
    BackHandler(enabled=selectedRoom!=null){selectedRoom=null}
    val room=rooms.find{it.optString("id")==selectedRoom}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        if(room!=null){
            item{TextButton(onClick={selectedRoom=null}){Text("‹  Toutes les pièces")};StatusCard(room.getString("name"),"Associez les appareils à cette pièce",MaisonIcons.Home)}
            if(message.isNotBlank())item{Notice(message)}
            if(devices.isEmpty())item{EmptyCard("Aucun appareil à associer","Ajoutez d’abord vos appareils dans la rubrique Appareils.",MaisonIcons.Devices)}
            items(devices,key={it.getString("id")}){d->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(MaisonIcons.Devices,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(d.getString("name"));val other=rooms.find{it.optString("id")==d.optString("roomId")};Text(other?.optString("name")?:"Sans pièce",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)};Checkbox(checked=d.optString("roomId")==room.getString("id"),enabled=!busy,onCheckedChange={checked->action{client.post("home/assign-room",JSONObject().put("deviceId",d.getString("id")).put("roomId",if(checked)room.getString("id")else JSONObject.NULL))}})}}}
        }else{
            item{Filters(listOf("Pièces","Scènes"),tab){tab=it}}
            item{Button(enabled=!busy,onClick={name="";message="";if(tab=="Pièces")createRoom=true else{step=0;chosen=emptySet();turnOn=false;createScene=true}},modifier=Modifier.fillMaxWidth()){Text(if(tab=="Pièces")"+ Ajouter une pièce" else "+ Créer une scène")}}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(message.isNotBlank())item{Notice(message)}
            if(tab=="Pièces"){
                if(rooms.isEmpty()&&!busy)item{EmptyCard("Créez votre première pièce","Salon, chambre, bureau… Organisez vos appareils comme votre maison.",MaisonIcons.Home)}
                items(rooms,key={it.getString("id")}){r->val count=devices.count{it.optString("roomId")==r.getString("id")};Card(onClick={selectedRoom=r.getString("id")},modifier=Modifier.fillMaxWidth()){Box(Modifier.fillMaxWidth().height(124.dp).background(Brush.linearGradient(listOf(Color(0xFF223D57),Color(0xFF162432))))){Icon(MaisonIcons.Home,null,tint=Color(0xFF80B4E3).copy(alpha=.25f),modifier=Modifier.align(Alignment.CenterEnd).padding(20.dp).size(72.dp));Column(Modifier.align(Alignment.BottomStart).padding(18.dp)){Text(r.getString("name"),style=MaterialTheme.typography.titleLarge);Text("$count appareil${if(count>1)"s" else ""}",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)}}}}
            }else{
                if(scenes.isEmpty()&&!busy)item{EmptyCard("Votre première ambiance","Choisissez des appareils et ce qu’ils doivent faire, puis lancez la scène en un toucher.",MaisonIcons.PlayCircle)}
                items(scenes,key={it.getString("id")}){s->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(MaisonIcons.Home,null,tint=Color(0xFFD28AF7),modifier=Modifier.size(30.dp));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(s.getString("name"),style=MaterialTheme.typography.titleMedium);Text("${s.optJSONArray("actions")?.length()?:0} action(s)",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)};IconButton(enabled=!busy,onClick={action{val results=JSONObject(client.post("scenes/${s.getString("id")}/run")).getJSONArray("results").objects();message=if(results.all{it.optBoolean("success")})"Scène exécutée." else "Certaines actions n’ont pas abouti. Vérifiez les appareils."}}){Icon(MaisonIcons.PlayCircle,contentDescription="Lancer ${s.getString("name")}",tint=MaterialTheme.colorScheme.primary)}}}}
            }
        }
    }
    if(createRoom)AlertDialog(onDismissRequest={if(!busy)createRoom=false},title={Text("Nouvelle pièce")},text={Column{OutlinedTextField(name,{name=it},label={Text("Nom de la pièce")},singleLine=true,modifier=Modifier.fillMaxWidth());if(message.isNotBlank())Text(message)}},confirmButton={TextButton(enabled=!busy&&name.isNotBlank(),onClick={action{client.post("rooms",JSONObject().put("name",name.trim()));createRoom=false;name=""}}){Text("Créer")}},dismissButton={TextButton(enabled=!busy,onClick={createRoom=false}){Text("Annuler")}})
    if(createScene)AlertDialog(onDismissRequest={if(!busy)createScene=false},title={Text("Nouvelle scène · ${step+1}/3")},text={LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text(listOf("Nommez votre ambiance","Choisissez les appareils","Définissez l’action")[step],style=MaterialTheme.typography.titleMedium)}
        when(step){
            0->item{OutlinedTextField(name,{name=it},label={Text("Nom de la scène")},placeholder={Text("Ex. Soirée détente")},singleLine=true,modifier=Modifier.fillMaxWidth())}
            1->{if(devices.isEmpty())item{Text("Ajoutez d’abord des appareils compatibles.",color=MaisonMuted)};items(devices){d->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(d.getString("id") in chosen,{checked->chosen=if(checked)chosen+d.getString("id")else chosen-d.getString("id")});Text(d.getString("name"))}}}
            2->{item{Text(name,style=MaterialTheme.typography.titleLarge);Text("${chosen.size} appareil(s) sélectionné(s)",color=MaisonMuted)};item{Row(verticalAlignment=Alignment.CenterVertically){RadioButton(turnOn,{turnOn=true});Text("Allumer les appareils")};Row(verticalAlignment=Alignment.CenterVertically){RadioButton(!turnOn,{turnOn=false});Text("Éteindre les appareils")}}}
        }
        if(message.isNotBlank())item{Text(message,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!busy&&when(step){0->name.isNotBlank();else->chosen.isNotEmpty()},onClick={if(step<2)step++ else action{val actions=JSONArray();chosen.forEach{actions.put(JSONObject().put("deviceId",it).put("values",JSONObject().put("on",turnOn)))};client.post("scenes",JSONObject().put("name",name.trim()).put("actions",actions));createScene=false;message="Scène créée."}}){Text(if(step<2)"Continuer" else "Créer la scène")}},dismissButton={TextButton(enabled=!busy,onClick={if(step>0)step-- else createScene=false}){Text(if(step>0)"Retour" else "Annuler")}})
}
