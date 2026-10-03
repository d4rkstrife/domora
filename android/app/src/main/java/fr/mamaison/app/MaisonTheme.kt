package fr.mamaison.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val MaisonMuted=Color(0xFF9BB0C4)
val MaisonGreen=Color(0xFF51D88A)
@Composable fun MaisonTheme(content:@Composable ()->Unit){
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF239BFF),onPrimary=Color.White,background=Color(0xFF071119),surface=Color(0xFF12212D),surfaceVariant=Color(0xFF182A38),onSurface=Color(0xFFF5F8FC),onSurfaceVariant=MaisonMuted,outline=Color(0xFF314555)),shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(24.dp)),content=content)
}
@Composable fun MenuCard(title:String,subtitle:String,icon:ImageVector,tint:Color=Color(0xFF9BC6FA),onClick:()->Unit){
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(16.dp)){
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(44.dp).background(tint.copy(alpha=.12f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=tint,modifier=Modifier.size(25.dp))}
            Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(3.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaisonMuted)}
            Spacer(Modifier.width(8.dp));Icon(MaisonIcons.Chevron,null,tint=MaisonMuted,modifier=Modifier.size(16.dp))
        }
    }
}
@Composable fun StatusCard(title:String,subtitle:String,icon:ImageVector=MaisonIcons.Dns){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123A60),Color(0xFF102536)))).padding(20.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=Color(0xFF55BDFF),modifier=Modifier.size(32.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(4.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Color(0xFFC3D8EB))}
        }
    }
}
