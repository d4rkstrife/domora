package fr.mamaison.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TvLink {
    fun parse(text:String):JSONObject {
        require(text.startsWith("domora-tv://pair?request=")){"Ce QR code n’est pas un appairage Domora TV."}
        require(text.length<5000){"QR code trop volumineux"}
        val result=JSONObject(String(Base64.decode(text.substringAfter("request="),Base64.URL_SAFE or Base64.NO_WRAP),Charsets.UTF_8))
        require(result.getInt("schema")==1 && result.getLong("expires")-System.currentTimeMillis() in 1..900000){"Ce QR code est expiré. Relancez l’appairage sur la télé."}
        val host=result.getString("host");val parts=host.split('.').map{it.toIntOrNull()?:-1}
        require(parts.size==4 && parts.all{it in 0..255} && (parts[0]==10 || parts[0]==192&&parts[1]==168 || parts[0]==172&&parts[1] in 16..31)){"La télé doit être sur votre réseau Wi-Fi local."}
        require(Base64.decode(result.getString("secret"),Base64.NO_WRAP).size==32){"Demande TV invalide"}
        require(result.getString("publicKey").length<1000 && result.getString("wireguardKey").length==44)
        return result
    }
    fun mac(secret:String,body:String):String = Base64.encodeToString(Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(Base64.decode(secret,Base64.NO_WRAP),"HmacSHA256"))}.doFinal(body.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    suspend fun deliver(request:JSONObject,approval:JSONObject)=withContext(Dispatchers.IO){
        require(System.currentTimeMillis()<request.getLong("expires")){"Appairage expiré. Relancez le QR code."}
        val body=approval.toString()
        val packet=JSONObject().put("body",body).put("mac",mac(request.getString("secret"),body)).toString()
        val connection=URL("http://${request.getString("host")}:8846/approve").openConnection() as HttpURLConnection
        try { connection.instanceFollowRedirects=false;connection.connectTimeout=4000;connection.readTimeout=8000;connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(packet.toByteArray())}
            require(connection.responseCode==200){"La télé n’a pas accepté l’autorisation. Vérifiez le QR et le même Wi-Fi."}
        } finally {connection.disconnect()}
    }
}
