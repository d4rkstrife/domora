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

@Composable fun DashboardScreen(client:MaisonClient,stats:JSONObject,navigate:(String)->Unit,refresh:()->Unit,loading:Boolean=false,serverError:String=""){
    var deviceCount by remember{mutableStateOf<Int?>(null)}
    var cameraCount by remember{mutableStateOf<Int?>(null)}
    var downloadCount by remember{mutableStateOf<Int?>(null)}
    var countsLoading by remember{mutableStateOf(true)}
    LaunchedEffect(stats){
        countsLoading=true
        deviceCount=null;cameraCount=null;downloadCount=null
        try{deviceCount=JSONArray(client.get("home/devices")).length()}catch(e:CancellationException){throw e}catch(_:Exception){}
        try{cameraCount=JSONArray(client.get("cameras")).length()}catch(e:CancellationException){throw e}catch(_:Exception){}
        try{downloadCount=JSONArray(client.get("downloads")).length()}catch(e:CancellationException){throw e}catch(_:Exception){}
        countsLoading=false
    }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text(stats.optString("name").ifBlank{"Votre maison"},style=MaterialTheme.typography.headlineSmall)
                    Text(if(loading)"Vérification de la connexion…" else if(serverError.isNotBlank())"Serveur injoignable · dernières données connues" else if(stats.length()>0)"● Serveur disponible" else "État du serveur indisponible",color=if(serverError.isNotBlank())MaisonWarning else if(stats.length()>0&&!loading)MaisonGreen else MaisonMuted,style=MaterialTheme.typography.bodySmall)
                    Text(readableConnection(client.connectionLabel),color=MaisonMuted,style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if(loading||countsLoading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(serverError.isNotBlank())item{Notice(serverError,isError=true)}
        item{SectionHeading("À portée de main")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            DashboardShortcut("Caméras","Voir le direct",MaisonIcons.Videocam,Modifier.weight(1f)){navigate("Caméras")}
            DashboardShortcut("Téléchargements","Suivre les transferts",MaisonIcons.Download,Modifier.weight(1f)){navigate("Téléchargements")}
        }}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            DashboardShortcut("Mes vidéos","Lire et reprendre",MaisonIcons.PlayCircle,Modifier.weight(1f)){navigate("Médias")}
            DashboardShortcut("Appareils","Commander la maison",MaisonIcons.Devices,Modifier.weight(1f)){navigate("Appareils")}
        }}
        item{SectionHeading("Votre installation","Informations disponibles sur le serveur")}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            DashboardCount("Appareils locaux",deviceCount)
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
            DashboardCount("Caméras",cameraCount)
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.3f))
            DashboardCount("Téléchargements",downloadCount)
        }}}
        item{TextButton(onClick=refresh,enabled=!loading){Text("Actualiser le tableau de bord")}}
    }
}
@Composable private fun DashboardCount(label:String,count:Int?){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=MaisonMuted);Text(count?.toString()?:"—",style=MaterialTheme.typography.titleSmall)}
}
@Composable private fun DashboardShortcut(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){
    Card(onClick=onClick,modifier=modifier.heightIn(min=136.dp)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(subtitle,color=MaisonMuted,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun SeedboxScreen(stats:JSONObject,navigate:(String)->Unit){
    val storage=stats.optJSONObject("storage")
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)){
        item{StatusCard("Votre seedbox","Téléchargements et fichiers sur votre serveur",MaisonIcons.Dns)}
        item{SectionHeading("Téléchargements")}
        item{MenuCard("Gérer les téléchargements","Ajouter un magnet ou un torrent, suivre et mettre en pause",MaisonIcons.Download){navigate("Téléchargements")}}
        item{SectionHeading("Fichiers")}
        item{MenuCard("Parcourir les fichiers","Dossiers partagés et actions sur vos documents",MaisonIcons.Folder){navigate("Fichiers")}}
        item{SectionHeading("Stockage")}
        item{MenuCard("Espace disponible",if(storage!=null&&storage.has("free"))bytes(storage.optDouble("free"))+" libres · voir le détail" else "Consulter les informations du volume partagé",MaisonIcons.Storage){navigate("Stockage")}}
    }
}
