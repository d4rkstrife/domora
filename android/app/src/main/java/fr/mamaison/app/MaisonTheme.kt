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

val MaisonMuted=Color(0xFFA6ACBF)
val MaisonGreen=Color(0xFF51D88A)
val MaisonWarning=Color(0xFFFFBD69)
@Composable fun MaisonTheme(content:@Composable ()->Unit){
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFB1A1FF),onPrimary=Color(0xFF20144F),primaryContainer=Color(0xFF3A2E66),onPrimaryContainer=Color(0xFFE8E0FF),secondary=Color(0xFFA9B9EC),background=Color(0xFF0C0E16),surface=Color(0xFF181B28),surfaceVariant=Color(0xFF232738),onSurface=Color(0xFFF5F5FC),onSurfaceVariant=MaisonMuted,outline=Color(0xFF45495E),error=Color(0xFFFF969B),errorContainer=Color(0xFF48232F)),shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp)),content=content)
}
@Composable fun MenuCard(title:String,subtitle:String,icon:ImageVector,tint:Color=Color(0xFFB1A1FF),onClick:()->Unit){
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(16.dp)){
        Row(Modifier.fillMaxWidth().heightIn(min=80.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(44.dp).background(tint.copy(alpha=.12f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=tint,modifier=Modifier.size(25.dp))}
            Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(3.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaisonMuted)}
            Spacer(Modifier.width(8.dp));Icon(MaisonIcons.Chevron,null,tint=MaisonMuted,modifier=Modifier.size(16.dp))
        }
    }
}
@Composable fun StatusCard(title:String,subtitle:String,icon:ImageVector=MaisonIcons.Dns){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF302845),Color(0xFF1B2031)))).padding(20.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(4.dp));Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Color(0xFFCFCCDF))}
        }
    }
}
@Composable internal fun SectionHeading(title:String,subtitle:String=""){
    Column(Modifier.padding(top=8.dp,bottom=2.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaisonMuted)
    }
}
