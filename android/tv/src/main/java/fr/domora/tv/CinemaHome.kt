package fr.domora.tv

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import fr.mamaison.app.MaisonClient
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.Collator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Accent=Color(0xff7777ff)
private val Ink=Color(0xff03080d)
private object ArtworkCache {val images=object:LruCache<String,Bitmap>(16*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount}}

@Composable private fun artwork(client:MaisonClient,video:JSONObject?,wide:Boolean):Bitmap?{
 val file=video?.optString("path").orEmpty();val meta=video?.optJSONObject("metadata")
 val asset=if(wide)meta?.optString("backdrop") else meta?.optString("poster")
 val cacheKey="$file|$wide|$asset"
 var image by remember(cacheKey){mutableStateOf(ArtworkCache.images.get(cacheKey))}
 LaunchedEffect(cacheKey,video?.optBoolean("posterReady")){
  if(file.isBlank()||image!=null)return@LaunchedEffect
  if(wide&&(asset.isNullOrBlank()||asset=="null"))return@LaunchedEffect
  if(!wide&&video?.optBoolean("posterReady")!=true)return@LaunchedEffect
  try{image=client.poster(file,backdrop=wide);image?.let{ArtworkCache.images.put(cacheKey,it)}}catch(e:CancellationException){throw e}catch(_:Exception){}
 }
 return image
}

@Composable private fun CinemaIcon(kind:String,modifier:Modifier=Modifier.size(18.dp),color:Color=Color.White){
 Canvas(modifier){val w=size.width;val h=size.height;val stroke=Stroke(width=w*.09f,cap=StrokeCap.Round)
  when(kind){
   "play"->drawPath(Path().apply{moveTo(w*.28f,h*.15f);lineTo(w*.85f,h*.5f);lineTo(w*.28f,h*.85f);close()},color)
   "plus"->{drawLine(color,Offset(w*.5f,h*.12f),Offset(w*.5f,h*.88f),w*.09f,StrokeCap.Round);drawLine(color,Offset(w*.12f,h*.5f),Offset(w*.88f,h*.5f),w*.09f,StrokeCap.Round)}
   "check"->drawPath(Path().apply{moveTo(w*.15f,h*.5f);lineTo(w*.4f,h*.75f);lineTo(w*.87f,h*.2f)},color,style=stroke)
   else->{drawCircle(color,w*.4f,Offset(w*.5f,h*.5f),style=stroke);drawCircle(color,w*.055f,Offset(w*.5f,h*.3f));drawLine(color,Offset(w*.5f,h*.47f),Offset(w*.5f,h*.73f),w*.085f,StrokeCap.Round)}
  }
 }
}

@Composable private fun CinemaButton(text:String,icon:String,primary:Boolean=false,onClick:()->Unit){
 var focused by remember{mutableStateOf(false)}
 Row(Modifier.height(33.dp).widthIn(min=if(text.isEmpty())36.dp else 80.dp).onFocusChanged{focused=it.isFocused}.border(if(focused)2.dp else 0.dp,if(focused)Color.White else Color.Transparent,RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(if(primary)Accent else Color(0xe019202a)).clickable(onClick=onClick).padding(horizontal=if(text.isEmpty())10.dp else 13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
  CinemaIcon(icon,color=if(primary)Color(0xff0a0b1c) else Color.White)
  if(text.isNotEmpty())Text(text,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=if(primary)Color(0xff0a0b1c) else Color.White)
 }
}

@Composable fun CinemaHome(client:MaisonClient,videos:List<JSONObject>,status:String,busy:Boolean,onRefresh:()->Unit,onCredits:()->Unit,onPlay:(JSONObject)->Unit){
 val context=LocalContext.current
 val collator=remember{Collator.getInstance(Locale.FRANCE).apply{strength=Collator.SECONDARY}}
 val ordered=remember(videos){videos.sortedWith{a,b->val byTitle=collator.compare(a.optString("title"),b.optString("title"));if(byTitle!=0)byTitle else a.optString("path").compareTo(b.optString("path"))}}
 val prefs=remember{context.getSharedPreferences("domora-tv-library",0)}
 var saved by remember{mutableStateOf(prefs.getStringSet("watchlist",emptySet())!!.toSet())}
 var listOnly by remember{mutableStateOf(false)};var profile by remember{mutableStateOf(false)};var info by remember{mutableStateOf(false)}
 var focusedPath by remember{mutableStateOf<String?>(null)}
 val visible=if(listOnly)ordered.filter{it.optString("path") in saved} else ordered
 val current=visible.firstOrNull{it.optString("path")==focusedPath}?:visible.firstOrNull()
 val meta=current?.optJSONObject("metadata")
 val background=artwork(client,current,true)
 var now by remember{mutableStateOf(LocalDateTime.now())}
 LaunchedEffect(Unit){while(true){now=LocalDateTime.now();delay(15000)}}
 val resume=visible.filter{val p=it.optJSONObject("progress");val position=p?.optDouble("position",0.0)?:0.0;val duration=p?.optDouble("duration",0.0)?:0.0;position>0&&(duration<=0||position/duration<.97)}
 fun toggleSaved(){current?.optString("path")?.let{file->saved=if(file in saved)saved-file else saved+file;prefs.edit().putStringSet("watchlist",saved).apply()}}
 BoxWithConstraints(Modifier.fillMaxSize().background(Ink)){
  val heroHeight=maxHeight*.405f
  val thumbHeight=maxHeight*.153f
  val posterHeight=maxHeight*.225f
  Image(painterResource(R.drawable.domora_landscape),null,Modifier.fillMaxWidth().height(maxHeight*.70f),contentScale=ContentScale.Crop)
  if(background!=null)Image(background.asImageBitmap(),null,Modifier.fillMaxWidth().height(maxHeight*.65f),contentScale=ContentScale.Crop)
  Box(Modifier.fillMaxWidth().height(maxHeight*.70f).background(Brush.horizontalGradient(listOf(Color(0xe603080d),Color(0x9803080d),Color.Transparent))))
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x1a03080d),.35f to Color(0x0003080d),.57f to Ink,1f to Ink)))
  Column(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){Image(painterResource(R.drawable.domora_mark),"Domora",Modifier.size(33.dp));Column{Text("Domora",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("Votre médiathèque maison",fontSize=9.sp,color=Color(0xffa0a6b0))}}
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){Column(horizontalAlignment=Alignment.End){Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=18.sp);Text(now.format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.FRANCE)),fontSize=9.sp,color=Color(0xffd5d5de))};var avatarFocus by remember{mutableStateOf(false)};Image(painterResource(R.drawable.domora_panda),"Profil et paramètres",Modifier.size(35.dp).onFocusChanged{avatarFocus=it.isFocused}.border(if(avatarFocus)2.dp else 0.dp,Color.White,CircleShape).clip(CircleShape).clickable{profile=true})}
   }
   Box(Modifier.fillMaxWidth().height(heroHeight-45.dp)){
    Column(Modifier.widthIn(max=360.dp).align(Alignment.CenterStart),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text(current?.optString("title")?:if(listOnly)"Votre liste personnelle" else "Votre cinéma, chez vous",fontSize=30.sp,lineHeight=31.sp,fontWeight=FontWeight.Black,maxLines=2,overflow=TextOverflow.Ellipsis)
     val series=current?.optString("path")?.contains(Regex("(?i)S\\d{1,2}E\\d{1,3}"))==true
     val details=listOfNotNull(if(series)"Série" else "Film",meta?.optString("year")?.takeIf{it.isNotBlank()},meta?.optJSONArray("genres")?.let{a->(0 until a.length()).take(2).joinToString(", "){a.optString(it)}}?.takeIf{it.isNotBlank()},meta?.optInt("runtime",0)?.takeIf{it>0}?.let{"${it/60} h ${it%60} min"}).joinToString("  •  ")
     Text(if(current==null)status else details,fontSize=10.sp,color=Color(0xffd7d9df),maxLines=1,overflow=TextOverflow.Ellipsis)
     Text(meta?.optString("overview")?.takeIf{it.isNotBlank()}?:if(listOnly&&visible.isEmpty())"Ajoutez des films avec le bouton Ma liste pour les retrouver ici." else "Vos films et séries, réunis sur votre serveur personnel.",fontSize=11.sp,lineHeight=14.sp,color=Color(0xffebedf2),maxLines=3,overflow=TextOverflow.Ellipsis)
     Spacer(Modifier.height(2.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      if(current!=null){CinemaButton("Regarder","play",true){onPlay(current)};val inList=current.optString("path") in saved;CinemaButton(if(inList)"Dans ma liste" else "Ma liste",if(inList)"check" else "plus"){toggleSaved()};CinemaButton("","info"){info=true}}
      else CinemaButton(if(busy)"Connexion…" else "Actualiser","info"){onRefresh()}
     }
    }
    Row(Modifier.align(Alignment.BottomCenter),horizontalArrangement=Arrangement.spacedBy(7.dp)){
     visible.take(5).forEach{video->Box(Modifier.size(6.dp).clip(CircleShape).background(if(video===current)Accent else Color(0xff656975)).clickable{focusedPath=video.optString("path")})}
    }
   }
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color=Accent)
   if(resume.isNotEmpty()){
    Text("Reprendre la lecture",fontSize=14.sp,fontWeight=FontWeight.Bold)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(3.dp)){
     items(resume,key={"resume:"+it.getString("path")}){video->CinemaTile(client,video,thumbHeight,true,{focusedPath=video.optString("path")}){onPlay(video)}}
    }
   }
   Text(if(listOnly)"Ma liste · A–Z" else "Films et séries · A–Z",fontSize=14.sp,fontWeight=FontWeight.Bold)
   LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(3.dp),modifier=Modifier.weight(1f)){
    items(visible,key={it.getString("path")}){video->CinemaTile(client,video,posterHeight,false,{focusedPath=video.optString("path")}){onPlay(video)}}
   }
   if(visible.isEmpty()&&!busy)Text(if(listOnly)"Votre liste est vide." else status,fontSize=11.sp,color=Color(0xffb7bac4))
  }
 }
 if(profile)AlertDialog(onDismissRequest={profile=false},title={Text("Votre espace Domora")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(status);OutlinedButton(onClick={listOnly=!listOnly;focusedPath=null;profile=false}){Text(if(listOnly)"Toute la bibliothèque" else "Ma liste (${saved.size})")};OutlinedButton(onClick={profile=false;onRefresh()},enabled=!busy){Text("Actualiser la bibliothèque")};TextButton(onClick={profile=false;onCredits()}){Text("À propos et crédits")}}},confirmButton={TextButton(onClick={profile=false}){Text("Fermer")}})
 if(info&&current!=null)AlertDialog(onDismissRequest={info=false},title={Text(current.optString("title"))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(meta?.optString("year").orEmpty());Text(meta?.optString("overview")?.takeIf{it.isNotBlank()}?:"Aucun résumé disponible pour ce fichier.")}},confirmButton={TextButton(onClick={info=false;onPlay(current)}){Text("Regarder")}},dismissButton={TextButton(onClick={info=false}){Text("Fermer")}})
}

@Composable private fun CinemaTile(client:MaisonClient,video:JSONObject,height:Dp,wide:Boolean,onFocus:()->Unit,onClick:()->Unit){
 var focused by remember{mutableStateOf(false)};val scale by animateFloatAsState(if(focused)1.035f else 1f,label="tileFocus")
 val poster=artwork(client,video,false);val landscape=if(wide)artwork(client,video,true) else null
 val image=landscape?:poster
 val width=if(wide)height*1.72f else height*.73f
 Box(Modifier.width(width).height(height).scale(scale).onFocusChanged{focused=it.isFocused;if(it.isFocused)onFocus()}.border(if(focused)2.dp else 0.dp,if(focused)Accent else Color.Transparent,RoundedCornerShape(7.dp)).padding(if(focused)2.dp else 0.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xff20242e)).clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=onClick)){
  if(image!=null)Image(image.asImageBitmap(),video.optString("title"),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  else Image(painterResource(R.drawable.domora_landscape),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,.45f to Color.Transparent,1f to Color(0xf503080d))))
  Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
   Text(video.optString("title"),fontSize=if(wide)11.sp else 10.sp,lineHeight=12.sp,fontWeight=FontWeight.SemiBold,maxLines=if(wide)1 else 2,overflow=TextOverflow.Ellipsis)
   if(wide){val p=video.optJSONObject("progress");val pos=p?.optDouble("position",0.0)?:0.0;val duration=p?.optDouble("duration",0.0)?:0.0;val remaining=((duration-pos)/60).toInt();Text(if(remaining>0)"$remaining min restantes" else "Reprendre à ${(pos/60).toInt()} min",fontSize=9.sp,color=Color(0xffc1c3cd));if(duration>0)LinearProgressIndicator(progress={(pos/duration).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(3.dp),color=Accent,trackColor=Color(0xff30333f))}
  }
 }
}
