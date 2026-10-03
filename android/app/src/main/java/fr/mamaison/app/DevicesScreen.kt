package fr.mamaison.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun deviceOn(device:JSONObject)=device.optJSONObject("state")?.let{if(it.has("on"))it.optBoolean("on")else it.optBoolean("output")}?:false
@Composable fun HomeScreen(client:MaisonClient){
    val scope=rememberCoroutineScope();var devices by remember{mutableStateOf(listOf<JSONObject>())};var rooms by remember{mutableStateOf(listOf<JSONObject>())};var found by remember{mutableStateOf(listOf<JSONObject>())}
    var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var selectedId by rememberSaveable{mutableStateOf<String?>(null)};var filter by rememberSaveable{mutableStateOf("Tous")};var discover by remember{mutableStateOf(false)};var brightness by remember{mutableStateOf(50f)}
    suspend fun refresh(){devices=JSONArray(client.get("home/devices")).objects();rooms=JSONArray(client.get("rooms")).objects()}
    fun action(work:suspend ()->Unit){scope.launch{busy=true;message="";try{work();refresh()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Commande impossible"}finally{busy=false}}}
    LaunchedEffect(Unit){action{}}
    val selected=devices.find{it.optString("id")==selectedId}
    BackHandler(enabled=selectedId!=null){selectedId=null}
    fun room(d:JSONObject)=rooms.find{it.optString("id")==d.optString("roomId")}?.optString("name")?:"Sans pièce"
    fun open(d:JSONObject){selectedId=d.getString("id");brightness=(d.optJSONObject("state")?.optDouble("brightness",50.0)?:50.0).toFloat().coerceIn(0f,100f)}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        if(selected==null){
            item{Filters(listOf("Tous","Lumières","Prises"),filter){filter=it}}
            item{Button(enabled=!busy,onClick={discover=true;action{val result=JSONObject(client.post("home/discover"));val existing=devices.map{it.optString("sourceId")}.toSet();found=result.getJSONArray("devices").objects().filter{it.optString("sourceId") !in existing};message=if(found.isEmpty())"Aucun nouvel appareil Shelly compatible trouvé." else "${found.size} appareil(s) à ajouter."}},modifier=Modifier.fillMaxWidth()){Text(if(busy)"Recherche…" else "+ Ajouter un appareil")}}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(message.isNotBlank())item{Notice(message)}
            if(devices.isEmpty()&&!busy)item{EmptyCard("Votre maison attend ses appareils","Recherchez vos appareils Shelly compatibles sur le réseau local.",MaisonIcons.Devices)}
            items(devices.filter{filter=="Tous"||if(filter=="Prises")it.optString("type")=="switch" else it.optString("type")!="switch"},key={it.getString("id")}){d->
                val light=d.optString("type")!="switch";val tint=if(light)Color(0xFFFFC34A)else Color(0xFF56C3E0)
                Card(onClick={open(d)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(tint.copy(alpha=.14f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(if(light)MaisonIcons.Lightbulb else MaisonIcons.Devices,null,tint=tint)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(d.getString("name"),style=MaterialTheme.typography.titleMedium);Text(room(d),style=MaterialTheme.typography.bodySmall,color=MaisonMuted)};Switch(checked=deviceOn(d),enabled=!busy,onCheckedChange={on->action{client.post("home/devices/${d.getString("id")}/control",JSONObject().put("on",on))}})}}
            }
            if(devices.isNotEmpty())item{Text("Les interrupteurs affichent le dernier état connu du serveur.",style=MaterialTheme.typography.bodySmall,color=MaisonMuted)}
        }else{
            item{TextButton(onClick={selectedId=null}){Text("‹  Mes appareils")}}
            item{StatusCard(selected.getString("name"),room(selected),if(selected.optString("type")=="switch")MaisonIcons.Devices else MaisonIcons.Lightbulb)}
            if(message.isNotBlank())item{Notice(message)}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            item{Card(Modifier.fillMaxWidth()){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Alimentation",style=MaterialTheme.typography.titleMedium);Text(if(deviceOn(selected))"Dernier état : allumé" else "Dernier état : éteint",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)};Switch(deviceOn(selected),enabled=!busy,onCheckedChange={on->action{client.post("home/devices/${selected.getString("id")}/control",JSONObject().put("on",on))}})}}}
            if(selected.optJSONObject("capabilities")?.optBoolean("brightness")==true)item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Luminosité",style=MaterialTheme.typography.titleMedium);Text("${brightness.toInt()} %")};Slider(brightness,{brightness=it},valueRange=0f..100f,enabled=!busy);Button(enabled=!busy,onClick={action{client.post("home/devices/${selected.getString("id")}/control",JSONObject().put("brightness",brightness.toInt()))}},modifier=Modifier.fillMaxWidth()){Text("Appliquer la luminosité")}}}}
            if(selected.optJSONObject("capabilities")?.optBoolean("color")==true)item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Couleur",style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(listOf(255,255,255),listOf(255,190,40),listOf(255,90,50),listOf(170,60,240),listOf(20,120,255),listOf(30,220,80)).forEach{rgb->val color=Color(rgb[0],rgb[1],rgb[2]);IconButton(enabled=!busy,onClick={action{client.post("home/devices/${selected.getString("id")}/control",JSONObject().put("rgb",JSONArray(rgb)))}},modifier=Modifier.weight(1f).size(44.dp).background(color,RoundedCornerShape(22.dp))){Icon(MaisonIcons.Lightbulb,contentDescription="Couleur ${rgb.joinToString(",")}",tint=Color.Black.copy(alpha=.5f),modifier=Modifier.size(18.dp))}}}}}}
        }
    }
    if(discover)AlertDialog(onDismissRequest={if(!busy)discover=false},title={Text("Ajouter un appareil")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Recherche des appareils Shelly compatibles sur votre réseau.",color=MaisonMuted);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(message.isNotBlank())Text(message)};items(found){d->OutlinedButton(enabled=!busy,onClick={action{client.post("home/devices",JSONObject().put("address",d.getString("address")).put("component",d.getString("component")));found=found.filter{it.optString("sourceId")!=d.optString("sourceId")};message="Appareil ajouté."}},modifier=Modifier.fillMaxWidth()){Text("+ ${d.getString("name")}")}}}},confirmButton={TextButton(enabled=!busy,onClick={discover=false}){Text("Terminer")}})
}
