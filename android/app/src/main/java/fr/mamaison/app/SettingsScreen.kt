package fr.mamaison.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

@Composable fun SettingsScreen(client:MaisonClient,page:String,navigate:(String)->Unit,backup:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var name by remember{mutableStateOf("")};var remote by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("")};var pairedCode by remember{mutableStateOf("")}
    var devices by remember{mutableStateOf(listOf<JSONObject>())};var revoke by remember{mutableStateOf<JSONObject?>(null)}
    var loading by remember{mutableStateOf(false)};var pending by remember{mutableStateOf(false)}
    var advanced by rememberSaveable{mutableStateOf(false)}
    val wireguard=remember{MaisonWireGuard.get(context)}
    var vpnEnabled by remember{mutableStateOf(wireguard.configured)}
    fun open(target:String){navigate(target)}
    fun action(work:suspend ()->Unit){scope.launch{pending=true;message="";try{work()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Action impossible. Réessayez."}finally{pending=false}}}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){NotificationsWorker.enable(context);message="Notifications activées."}else message="Vous pouvez autoriser les notifications dans les réglages Android."}
    val vpnPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->action{if(result.resultCode==android.app.Activity.RESULT_OK){vpnEnabled=true;message="Accès 5G prêt. Ma Maison se connectera automatiquement hors du Wi-Fi."}else{wireguard.disable();vpnEnabled=false;message="Autorisation refusée. Votre accès Wi-Fi reste disponible."}}}
    suspend fun refreshDevices(){val list=JSONArray(client.get("devices/authorized"));devices=(0 until list.length()).map{list.getJSONObject(it)}}
    LaunchedEffect(page){
        if(page in listOf("Maison (nom)","Utilisateurs","Paramètres")){
            loading=true
            try{if(page=="Utilisateurs")refreshDevices()else{val settings=JSONObject(client.get("settings"));name=settings.optString("name");remote=settings.optString("remoteUrl")}}
            catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Chargement impossible."}finally{loading=false}
        }
    }
    LazyColumn(modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){
        if(loading||pending)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(message.isNotBlank())item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Text(message,Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)}}
        when(page){
            "Réglages" -> {
                item{SectionHeading("Installation","Votre maison et sa connexion")}
                item{MenuCard("Installation","Nom de la maison, connexion et accès distant",MaisonIcons.Home){open("Installation")}}
                item{SectionHeading("Accès et autorisations")}
                item{MenuCard("Appareils autorisés","Ajouter un téléphone ou retirer un accès",MaisonIcons.People){open("Utilisateurs")}}
                item{MenuCard("Téléviseurs","Appairer Domora TV et choisir ses dossiers",MaisonIcons.Television){navigate("Téléviseurs")}}
                item{SectionHeading("Services connectés")}
                item{MenuCard("Intégrations","Shelly et autorisations Google Home",MaisonIcons.Link){open("Intégrations")}}
                item{SectionHeading("Préférences et maintenance")}
                item{MenuCard("Préférences et maintenance","Notifications, sauvegarde et réglages avancés",MaisonIcons.Settings){open("Paramètres")}}
                item{SectionHeading("Informations")}
                item{MenuCard("Aide et support","Connexion, caméra et lecture vidéo",MaisonIcons.Info){open("Aide & support")}}
                item{MenuCard("À propos","Domora · ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",MaisonIcons.Info){open("À propos")}}
            }
            "Installation" -> {
                item{MenuCard("Nom de la maison","Personnaliser le nom de votre installation",MaisonIcons.Home){open("Maison (nom)")}}
                item{MenuCard("Connexion et accès distant","État du réseau, accès 5G et vérification",MaisonIcons.Network){open("Réseau")}}
            }
            "Maison (nom)" -> {
                item{Text("Donnez un nom à votre maison",color=MaisonMuted);OutlinedTextField(name,{name=it},label={Text("Nom de la maison")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                item{Button(enabled=!pending&&!loading&&name.isNotBlank(),onClick={action{client.post("settings",JSONObject().put("name",name.trim()));message="Nom enregistré."}},modifier=Modifier.fillMaxWidth()){Text("Enregistrer le nom")}}
                item{MenuCard("Pièces et scènes","Organiser les appareils locaux",MaisonIcons.Home){navigate("Pièces et scènes")}}
                item{MenuCard("Appareils","Découvrir et contrôler les appareils connectés",MaisonIcons.Devices){navigate("Appareils")}}
            }
            "Utilisateurs" -> {
                item{Text("Partagez l’accès avec un autre téléphone. Chaque téléphone peut être retiré séparément.",color=MaisonMuted)}
                item{Button(enabled=!pending,onClick={action{pairedCode=JSONObject(client.post("pairing/open")).getString("code")}},modifier=Modifier.fillMaxWidth()){Text("Ajouter un téléphone")}}
                if(pairedCode.isNotEmpty())item{StatusCard(pairedCode,"Code d’appairage · valable 10 minutes",MaisonIcons.People)}
                items(devices,key={it.getString("id")}){device->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(device.getString("name"),style=MaterialTheme.typography.titleMedium);Text("${when(device.optString("role")){"admin"->"Administrateur";"tv"->"Télévision";else->"Utilisateur"}} · ${if(device.optBoolean("revoked"))"Accès retiré" else "Autorisé"}",color=MaisonMuted,style=MaterialTheme.typography.bodySmall);if(!device.optBoolean("revoked"))TextButton(enabled=!pending,onClick={revoke=device}){Text("Retirer l’accès",color=MaterialTheme.colorScheme.error)}}}}
            }
            "Intégrations" -> {
                item{StatusCard("Appareils Shelly","Découverte locale des modèles compatibles",MaisonIcons.Lightbulb)}
                item{MenuCard("Gérer les appareils","Rechercher les appareils sur votre réseau",MaisonIcons.Devices){navigate("Appareils")}}
                item{MenuCard("Google Home","Connecter votre compte et gérer vos lumières",MaisonIcons.Link){navigate("Google Home")}}
            }
            "Réseau" -> {
                item{StatusCard("Connexion actuelle",readableConnection(client.connectionLabel),MaisonIcons.Network)}
                item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Accès en 5G",style=MaterialTheme.typography.titleLarge);Text(if(vpnEnabled)"Activé · connexion automatique" else "Non activé",color=if(vpnEnabled)MaisonGreen else MaisonMuted);Text("Votre application retrouve le Pi hors de la maison grâce à son tunnel WireGuard intégré.",color=MaisonMuted)
                    if(!vpnEnabled)Button(enabled=!pending,onClick={action{client.authenticate();client.configureWireGuard();val permission=wireguard.permissionIntent();if(permission!=null)vpnPermission.launch(permission)else{vpnEnabled=true;message="Accès 5G configuré."}}},modifier=Modifier.fillMaxWidth()){Text("Configurer l’accès 5G")}
                    OutlinedButton(enabled=!pending,onClick={action{client.authenticate();message=client.checkRemote()}},modifier=Modifier.fillMaxWidth()){Text("Vérifier la connexion")}
                    if(vpnEnabled)TextButton(enabled=!pending,onClick={action{wireguard.disable();vpnEnabled=false;message="Accès 5G désactivé. Le Wi-Fi reste disponible."}}){Text("Désactiver l’accès 5G")}
                }}}
                item{Text("Pour tester : coupez le Wi-Fi, puis ouvrez une caméra ou une vidéo. Android demande une autorisation VPN lors de la première activation.",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)}
            }
            "Paramètres" -> {
                item{MenuCard("Notifications","Recevoir les alertes de Ma Maison",MaisonIcons.Info){if(android.os.Build.VERSION.SDK_INT>=33)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)else{NotificationsWorker.enable(context);message="Notifications activées."}}}
                item{MenuCard("Sauvegarder la configuration","Export JSON de configuration · sans les médias",MaisonIcons.Storage){backup()}}
                item{TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Masquer les réglages avancés" else "Réglages avancés")}}
                if(advanced){
                    item{Text("Relais HTTPS optionnel",style=MaterialTheme.typography.titleMedium);Text("Ce réglage concerne un relais séparé. Il n’est pas nécessaire pour votre accès WireGuard actuel.",color=MaisonMuted,style=MaterialTheme.typography.bodySmall)}
                    item{OutlinedTextField(remote,{remote=it},label={Text("Adresse du relais HTTPS")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                    item{OutlinedButton(enabled=!pending&&!loading,onClick={action{client.post("settings",JSONObject().put("remoteUrl",remote.trim()));client.authenticate();message="Adresse du relais enregistrée."}},modifier=Modifier.fillMaxWidth()){Text("Enregistrer le relais")}}
                }
            }
            "Aide & support" -> {
                item{HelpCard("Impossible de se connecter ?","Sur le Wi-Fi, vérifiez que le Pi est allumé. En 5G, consultez Réseau pour vérifier l’accès intégré.")}
                item{HelpCard("Caméra indisponible ?","Vérifiez le branchement USB. Un autre logiciel utilisant la webcam peut empêcher l’ouverture du direct.")}
                item{HelpCard("Comment lire une vidéo ?","Ouvrez Médias, puis choisissez une vidéo. Les commandes apparaissent en touchant l’écran ; le bouton plein écran est dans le lecteur.")}
                item{MenuCard("Vérifier le réseau","Voir la connexion et l’accès 5G",MaisonIcons.Network){open("Réseau")}}
            }
            "À propos" -> {
                item{StatusCard("Domora","Votre maison et vos médias, sur votre serveur",MaisonIcons.Home)}
                item{Text("Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",style=MaterialTheme.typography.titleMedium);Text("Serveur personnel sur Raspberry Pi. Caméras, téléchargements, médias et appareils connectés.",color=MaisonMuted)}
            }
        }
    }
    if(revoke!=null)AlertDialog(onDismissRequest={revoke=null},title={Text("Retirer l’accès de ce téléphone ?")},text={Text("${revoke!!.getString("name")} ne pourra plus se connecter à votre maison.")},confirmButton={TextButton(onClick={val id=revoke!!.getString("id");revoke=null;action{client.delete("devices/authorized/$id");refreshDevices();message="Accès retiré."}}){Text("Retirer l’accès",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={revoke=null}){Text("Annuler")}})
}
@Composable private fun HelpCard(title:String,description:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(description,color=MaisonMuted)}}}

