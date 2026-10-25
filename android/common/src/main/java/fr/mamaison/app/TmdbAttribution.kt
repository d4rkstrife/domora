package fr.mamaison.app
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable fun TmdbAttribution(logo:Int){
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Crédits",style=MaterialTheme.typography.titleMedium)
  Image(painterResource(logo),"TMDB",Modifier.width(110.dp).height(18.dp))
  Text("This product uses the TMDB API but is not endorsed or certified by TMDB.",style=MaterialTheme.typography.bodySmall)
  Text("Métadonnées et affiches : themoviedb.org",style=MaterialTheme.typography.bodySmall)
 }
}
