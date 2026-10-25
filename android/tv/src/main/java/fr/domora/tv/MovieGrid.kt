package fr.domora.tv

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mamaison.app.MaisonClient
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

@Composable fun MovieGrid(client:MaisonClient,videos:List<JSONObject>,choose:(JSONObject)->Unit){
 var currentPath by remember{mutableStateOf<String?>(null)}
 val first=remember{FocusRequester()}
 LaunchedEffect(videos.firstOrNull()?.optString("path")){withFrameNanos{};runCatching{first.requestFocus()}}
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
  val movie=videos.firstOrNull{it.optString("path")==currentPath}?:videos.firstOrNull();val meta=movie?.optJSONObject("metadata")
  Column(Modifier.height(60.dp)){
   Text(movie?.optString("title")?:"Vos films",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
   Text(listOfNotNull(meta?.optString("year")?.takeIf{it.isNotBlank()},meta?.optString("overview")?.takeIf{it.isNotBlank()}?:"Choisissez un film avec la télécommande, puis appuyez sur OK.").joinToString("  ·  "),color=Color(0xffb7b7ba),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
  }
  BoxWithConstraints(Modifier.weight(1f)){
   val posterHeight=minOf(205.dp,maxOf(100.dp,maxHeight-105.dp))
   LazyVerticalGrid(GridCells.Adaptive(145.dp),contentPadding=PaddingValues(10.dp),horizontalArrangement=Arrangement.spacedBy(22.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
    items(videos,key={it.getString("path")}){video->PosterCard(client,video,posterHeight,if(video===videos.firstOrNull())Modifier.focusRequester(first) else Modifier,{currentPath=video.getString("path")}){choose(video)}}
   }
  }
 }
}

@Composable private fun PosterCard(client:MaisonClient,video:JSONObject,posterHeight:androidx.compose.ui.unit.Dp,modifier:Modifier,onFocus:()->Unit,onClick:()->Unit){
 var focused by remember{mutableStateOf(false)};var bitmap by remember(video.getString("path")){mutableStateOf<Bitmap?>(null)}
 val scale by animateFloatAsState(if(focused)1.055f else 1f,label="posterFocus")
 val ready=video.optBoolean("posterReady");val id=video.optJSONObject("metadata")?.optInt("id")
 LaunchedEffect(video.getString("path"),ready,id){if(ready)try{bitmap=client.poster(video.getString("path"))}catch(e:CancellationException){throw e}catch(_:Exception){bitmap=null}else bitmap=null}
 val interaction=remember{MutableInteractionSource()}
 Column(modifier.scale(scale).onFocusChanged{focused=it.isFocused;if(it.isFocused)onFocus()}.clickable(interactionSource=interaction,indication=null,onClick=onClick),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Box(Modifier.width(posterHeight*2f/3f).height(posterHeight).border(if(focused)4.dp else 1.dp,if(focused)Color.White else Color(0xff343438),RoundedCornerShape(8.dp)).padding(if(focused)4.dp else 1.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(listOf(Color(0xff34302f),Color(0xff171719)))),contentAlignment=Alignment.Center){
   val image=bitmap
   if(image!=null)Image(image.asImageBitmap(),video.optString("title"),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
   else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){Text("▶",style=MaterialTheme.typography.displayMedium,color=Color(0xff888383));Text(if(ready)"Chargement…" else "DOMORA",style=MaterialTheme.typography.labelSmall,color=Color(0xffaaa5a5))}
   if(focused)Surface(color=Color.White,contentColor=Color.Black,shape=RoundedCornerShape(4.dp),modifier=Modifier.align(Alignment.BottomCenter).padding(10.dp)){Text("▶  Regarder",Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)}
  }
  Text(video.optString("title"),color=if(focused)Color.White else Color(0xffc4c4c7),fontWeight=if(focused)FontWeight.Bold else FontWeight.Medium,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
  val progress=video.optJSONObject("progress");val position=progress?.optDouble("position",0.0)?:0.0;val duration=progress?.optDouble("duration",0.0)?:0.0
  Text(if(position>0)"Reprendre · ${(position/60).toInt()} min" else video.optJSONObject("metadata")?.optString("year")?:"",style=MaterialTheme.typography.bodySmall,color=Color(0xff969699))
  if(position>0&&duration>0)LinearProgressIndicator(progress={minOf(1f,(position/duration).toFloat())},color=Color.White,trackColor=Color(0xff343438),modifier=Modifier.fillMaxWidth().height(2.dp))
 }
}
