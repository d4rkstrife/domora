package fr.mamaison.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable fun DashboardScreen(client:MaisonClient,stats:JSONObject,navigate:(String)->Unit,refresh:()->Unit){
    var deviceCount by remember{mutableStateOf<Int?>(null)}
    var cameraCount by remember{mutableStateOf<Int?>(null)}
    LaunchedEffect(stats){
        try{deviceCount=JSONArray(client.get("home/devices")).length()}catch(e:CancellationException){throw e}catch(_:Exception){}
        try{cameraCount=JSONArray(client.get("cameras")).length()}catch(e:CancellationException){throw e}catch(_:Exception){}
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{StatusCard(stats.optString("name","Ma Maison"),"● Serveur disponible · ${client.connectionLabel}")}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            DashboardMetric("${deviceCount?:"—"}","appareils",Modifier.weight(1f)){navigate("Appareils")}
            DashboardMetric("${cameraCount?:"—"}","caméras",Modifier.weight(1f)){navigate("Caméras")}
            DashboardMetric("Ouvrir","seedbox",Modifier.weight(1f)){navigate("Seedbox")}
        }}
        item{Text("Raccourcis",style=MaterialTheme.typography.titleLarge)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Card(onClick={navigate("Médias")},modifier=Modifier.weight(1f)){Column(Modifier.padding(18.dp)){Icon(MaisonIcons.PlayCircle,null,tint=Color(0xFFA992FF));Spacer(Modifier.height(10.dp));Text("Mes médias",style=MaterialTheme.typography.titleSmall);Text("Films et vidéos",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)}}
            Card(onClick={navigate("Téléchargements")},modifier=Modifier.weight(1f)){Column(Modifier.padding(18.dp)){Icon(MaisonIcons.Storage,null,tint=Color(0xFF4FCCEC));Spacer(Modifier.height(10.dp));Text("Téléchargements",style=MaterialTheme.typography.titleSmall);Text("Suivre la seedbox",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)}}
        }}
        item{Text("Votre maison",style=MaterialTheme.typography.titleLarge)}
        item{MenuCard("Pièces et scènes","Organiser la maison et lancer une ambiance",MaisonIcons.Home,Color(0xFFF18ABC)){navigate("Pièces et scènes")}}
        item{MenuCard("Caméras","Voir ce qui se passe à la maison",MaisonIcons.Videocam){navigate("Caméras")}}
        item{Text("Votre serveur",style=MaterialTheme.typography.titleLarge)}
        item{MenuCard("Seedbox","Stockage, téléchargements et fichiers",MaisonIcons.Dns,MaisonGreen){navigate("Seedbox")}}
        item{TextButton(onClick=refresh){Text("Actualiser la maison")}}
    }
}
@Composable private fun DashboardMetric(value:String,label:String,modifier:Modifier,onClick:()->Unit){
    Card(onClick=onClick,modifier=modifier){Column(Modifier.padding(horizontal=12.dp,vertical=16.dp)){Text(value,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary);Text(label,style=MaterialTheme.typography.bodySmall,color=MaisonMuted)}}
}
@Composable fun SeedboxScreen(stats:JSONObject,navigate:(String)->Unit){
    val storage=stats.optJSONObject("storage")
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{StatusCard("Ma seedbox","Vos fichiers restent sur votre serveur",MaisonIcons.Dns)}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("Stockage disponible",color=MaisonMuted);Text(if(storage==null)"Information indisponible" else "${(storage.optDouble("free")/1e9).toInt()} Go",style=MaterialTheme.typography.headlineMedium)}}}
        item{MenuCard("Téléchargements","Ajouter, mettre en pause et suivre les torrents",MaisonIcons.Storage,Color(0xFF47C2FF)){navigate("Téléchargements")}}
        item{MenuCard("Fichiers","Parcourir les dossiers du serveur",MaisonIcons.Folder){navigate("Fichiers")}}
        item{MenuCard("Médias","Lire vos films et vos vidéos",MaisonIcons.PlayCircle,Color(0xFFA992FF)){navigate("Médias")}}
    }
}
