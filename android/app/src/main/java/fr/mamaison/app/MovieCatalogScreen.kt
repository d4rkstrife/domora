package fr.mamaison.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.*

@Composable fun MovieCatalogScreen(client:MaisonClient){
 val scope=rememberCoroutineScope();val context=LocalContext.current
 var accessGranted by remember{mutableStateOf(false)}
 var token by remember{mutableStateOf("")};var configured by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")};var films by remember{mutableStateOf<List<JSONObject>>(emptyList())}
 var confirmClear by remember{mutableStateOf(false)}
 var selected by remember{mutableStateOf<JSONObject?>(null)};var query by remember{mutableStateOf("")};var results by remember{mutableStateOf<List<JSONObject>>(emptyList())}
 fun array(raw:String)=JSONArray(raw).let{a->(0 until a.length()).map{a.getJSONObject(it)}}
 suspend fun refresh(){val status=JSONObject(client.get("media/catalog/settings"));accessGranted=true;configured=status.optBoolean("configured");films=array(client.get("media/catalog/library"));val issue=status.optString("error");if(issue.isNotEmpty())message=issue}
 fun action(work:suspend()->Unit){scope.launch{busy=true;message="";try{work()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Catalogue indisponible"}finally{busy=false}}}
 LaunchedEffect(Unit){action{refresh()}}
 if(!accessGranted){
  Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   StatusCard("Gestion des jaquettes","Accès réservé aux administrateurs de la maison",MaisonIcons.PlayCircle)
   if(message.isNotBlank())Notice(message)
   OutlinedButton(enabled=!busy,onClick={action{refresh()}}){Text("Réessayer")}
  }
  return
 }
 LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Text("Jaquettes des films",style=MaterialTheme.typography.headlineSmall);Text("Le Pi identifie les films à partir de leur nom et met les affiches en cache. Les cas ambigus restent à identifier.",color=MaisonMuted)}
  if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
  if(message.isNotEmpty())item{Text(message)}
  item{Text(if(configured)"TMDB connecté" else "Connecter TMDB",style=MaterialTheme.typography.titleMedium)
   Text("Créez un compte puis demandez un accès développeur gratuit. Copiez le jeton « API Read Access Token ». Le jeton reste sur votre serveur.",color=MaisonMuted)
   TextButton(onClick={context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.themoviedb.org/settings/api")))}){Text("Ouvrir les paramètres API TMDB")}
   OutlinedTextField(token,{token=it},label={Text("Jeton API de lecture TMDB")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
   Button(enabled=!busy&&token.isNotBlank(),onClick={action{client.post("media/catalog/settings",JSONObject().put("token",token));token="";client.post("media/catalog/scan");refresh();message="Recherche lancée. Actualisez la vidéothèque de la TV dans quelques instants."}}){Text("Enregistrer et rechercher les jaquettes")}
   if(configured)TextButton(enabled=!busy,onClick={action{client.post("media/catalog/settings",JSONObject().put("token",""));refresh()}}){Text("Déconnecter TMDB")}
  }
  item{Row{OutlinedButton(enabled=!busy,onClick={action{refresh()}}){Text("Actualiser")};if(configured)TextButton(enabled=!busy,onClick={action{client.post("media/catalog/scan");refresh();message="Recherche relancée."}}){Text("Relancer la recherche")}}}
  items(films,key={it.getString("path")}){film->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
   Text(film.optString("title"),style=MaterialTheme.typography.titleMedium)
   Text(film.getString("path"),style=MaterialTheme.typography.bodySmall,color=MaisonMuted)
   val state=film.optString("catalogState");Text(when(state){"matched"->"Identifié · ${film.optJSONObject("metadata")?.optString("year")?:""}";"pending"->"Recherche en cours…";"unconfigured"->"Jeton TMDB à configurer";else->"À identifier"},color=MaisonMuted)
   TextButton(enabled=!busy&&configured,onClick={selected=film;query=film.optString("title");results=emptyList()}){Text("Corriger l’identification")}
  }}}
  item{TmdbAttribution(R.drawable.tmdb_logo)}
 }
 if(selected!=null)AlertDialog(onDismissRequest={if(!busy)selected=null},title={Text("Identifier le film")},text={Column{
  OutlinedTextField(query,{query=it},label={Text("Titre du film")})
  TextButton(enabled=!busy&&query.isNotBlank(),onClick={action{results=array(client.get("media/catalog/search?query="+java.net.URLEncoder.encode(query,"UTF-8")))}}){Text("Rechercher")}
  androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=260.dp)){items(results){movie->TextButton(enabled=!busy,onClick={val file=selected!!.getString("path");action{client.post("media/catalog/identify",JSONObject().put("path",file).put("id",movie.getInt("id")));selected=null;refresh()}}){Text("${movie.optString("title")} (${movie.optString("year")})")}}}
 }},confirmButton={TextButton(enabled=!busy,onClick={selected=null}){Text("Fermer")}},dismissButton={TextButton(enabled=!busy,onClick={confirmClear=true}){Text("Retirer la correspondance",color=MaterialTheme.colorScheme.error)}})
 if(confirmClear&&selected!=null)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Retirer cette identification ?")},text={Text("La correspondance de ce film sera retirée. Le fichier vidéo sera conservé.")},confirmButton={TextButton(enabled=!busy,onClick={val file=selected!!.getString("path");confirmClear=false;action{client.post("media/catalog/identify",JSONObject().put("path",file).put("clear",true));selected=null;refresh()}}){Text("Retirer",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Annuler")}})
}
