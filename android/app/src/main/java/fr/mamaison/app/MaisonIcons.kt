package fr.mamaison.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Small project-owned vector set: no bitmap assets or complete icon catalogue.
object MaisonIcons {
    val Chevron = icon("Suivant", "M9,4 L17,12 L9,20 L7,18 L13,12 L7,6 Z")
    val People = icon("Utilisateurs", "M8,2 A4,4 0,1 1,8,10 A4,4 0,1 1,8,2 M2,12 L14,12 L16,21 L0,21 Z M18,4 A3,3 0,1 1,18,10 A3,3 0,1 1,18,4 M17,12 L22,12 L24,21 L18,21 Z")
    val Network = icon("Reseau", "M1,6 Q12,-2 23,6 L21,8 Q12,1 3,8 Z M5,11 Q12,5 19,11 L17,13 Q12,9 7,13 Z M9,16 Q12,13 15,16 L12,20 Z")
    val Settings = icon("Parametres", "M9,2 L15,2 L16,6 L20,5 L23,10 L20,13 L21,17 L16,20 L13,18 L9,22 L4,19 L5,15 L1,12 L4,7 L8,8 Z M12,8 A4,4 0,1 0,12,16 A4,4 0,1 0,12,8")
    val Link = icon("Integrations", "M3,8 L8,3 L13,3 L16,6 L14,8 L12,5 L9,5 L5,9 L5,12 L8,14 L6,16 L3,13 Z M8,17 L10,15 L12,19 L15,19 L19,15 L19,12 L16,10 L18,8 L21,11 L21,16 L16,21 L11,21 Z M8,14 L14,8 L16,10 L10,16 Z")
    private fun icon(name: String, data: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd).build()
    val Home = icon("Maison", "M12,2 L1,11 L3,13 L5,11 L5,22 L10,22 L10,15 L14,15 L14,22 L19,22 L19,11 L21,13 L23,11 Z")
    val Devices = icon("Appareils", "M2,3 L15,3 L15,17 L2,17 Z M4,5 L4,15 L13,15 L13,5 Z M16,9 L23,9 L23,22 L16,22 Z M18,11 L18,20 L21,20 L21,11 Z M5,19 L12,19 L12,21 L5,21 Z")
    val Videocam = icon("Camera", "M2,5 L16,5 L16,9 L22,6 L22,18 L16,15 L16,19 L2,19 Z M4,7 L4,17 L14,17 L14,7 Z")
    val PlayCircle = icon("Lecture", "M4,2 L22,12 L4,22 Z M7,7 L7,17 L16,12 Z")
    val MoreHoriz = icon("Plus", "M3,10 L7,10 L7,14 L3,14 Z M10,10 L14,10 L14,14 L10,14 Z M17,10 L21,10 L21,14 L17,14 Z")
    val Dns = icon("Serveur", "M3,2 L21,2 L21,11 L3,11 Z M5,4 L5,9 L19,9 L19,4 Z M3,13 L21,13 L21,22 L3,22 Z M5,15 L5,20 L19,20 L19,15 Z M7,6 L10,6 L10,8 L7,8 Z M7,17 L10,17 L10,19 L7,19 Z")
    val Storage = icon("Stockage", "M3,3 L21,3 L21,21 L3,21 Z M5,5 L5,19 L19,19 L19,5 Z M7,14 L17,14 L17,16 L7,16 Z")
    val Memory = icon("Memoire", "M6,6 L18,6 L18,18 L6,18 Z M8,8 L8,16 L16,16 L16,8 Z M9,2 L11,2 L11,6 L9,6 Z M13,2 L15,2 L15,6 L13,6 Z M9,18 L11,18 L11,22 L9,22 Z M13,18 L15,18 L15,22 L13,22 Z M2,9 L6,9 L6,11 L2,11 Z M18,9 L22,9 L22,11 L18,11 Z M2,13 L6,13 L6,15 L2,15 Z M18,13 L22,13 L22,15 L18,15 Z")
    val Info = icon("Information", "M3,3 L21,3 L21,21 L3,21 Z M5,5 L5,19 L19,19 L19,5 Z M11,7 L13,7 L13,9 L11,9 Z M11,11 L13,11 L13,17 L11,17 Z")
    val Folder = icon("Dossier", "M2,5 L10,5 L12,8 L22,8 L22,21 L2,21 Z M4,10 L4,19 L20,19 L20,10 Z")
    val Lightbulb = icon("Ampoule", "M8,3 L16,3 L19,7 L19,12 L15,17 L15,19 L9,19 L9,17 L5,12 L5,7 Z M9,5 L7,8 L7,11 L11,16 L13,16 L17,11 L17,8 L15,5 Z M9,21 L15,21 L15,23 L9,23 Z")
}
