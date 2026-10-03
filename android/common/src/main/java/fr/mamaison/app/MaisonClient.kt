package fr.mamaison.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.security.cert.CertificateFactory
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import java.io.IOException

class MaisonClient(context: Context) {
    private val prefs = context.getSharedPreferences("maison", Context.MODE_PRIVATE)
    var address: String = prefs.getString("address", "")!!
    private var token = ""
    private var remoteUrl = prefs.getString("remoteUrl", "")!!
    private var remoteActive = false
    private var vpnActive = false
    private val wireguard=MaisonWireGuard.get(context)
    private var nextLocalProbe = 0L
    private var pin = prefs.getString("certificatePin", "")!!
    private var sslContext: SSLContext? = null
    val connectionLabel get() = if(vpnActive)"Connexion 5G · WireGuard intégré" else if(remoteActive)"Connexion distante sécurisée" else if(address.startsWith("https:"))"Connexion locale chiffrée" else "Connexion locale de développement"
    val paired get() = prefs.getString("device", null) != null && address.isNotBlank()
    private val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "maison-device"
    private fun fingerprint(cert: X509Certificate) = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
    private fun configurePin() {
        if(pin.isBlank())return
        val trust = object:X509TrustManager {
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
            override fun checkClientTrusted(chain:Array<X509Certificate>,auth:String){throw java.security.cert.CertificateException("Client non pris en charge")}
            override fun checkServerTrusted(chain:Array<X509Certificate>,auth:String){if(chain.isEmpty()||fingerprint(chain[0])!=pin)throw java.security.cert.CertificateException("Identité du serveur modifiée");chain[0].checkValidity()}
        }
        sslContext=SSLContext.getInstance("TLS").apply{init(null,arrayOf(trust),null)}
    }
    private fun connection(url: String, remote: Boolean): HttpURLConnection {
        val c=URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects=false
        if(!remote&&c is HttpsURLConnection&&pin.isNotBlank()) { if(sslContext==null)configurePin();c.sslSocketFactory=sslContext!!.socketFactory;c.hostnameVerifier=javax.net.ssl.HostnameVerifier { _,session -> try{fingerprint(session.peerCertificates[0] as X509Certificate)==pin}catch(_:Exception){false} } }
        c.connectTimeout=if((remoteUrl.isNotBlank()||wireguard.configured)&&!remote&&!vpnActive)2000 else 8000;c.readTimeout=15000
        return c
    }
    private suspend fun secureLocal() = withContext(Dispatchers.IO) {
        if(address.startsWith("https:")){configurePin();return@withContext}
        if(pin.isBlank()){
            val discoveryConnection=connection("$address/api/v1/discovery",false)
            try{
                val discovery=JSONObject(discoveryConnection.inputStream.bufferedReader().use{it.readText()})
                if(discovery.optString("version")=="0.1.0")return@withContext
            }finally{discoveryConnection.disconnect()}
        }
        val c=connection("$address/api/v1/security",false)
        try {
            if(c.responseCode==404&&pin.isBlank()){
                val legacy=connection("$address/api/v1/discovery",false)
                try{val discovery=JSONObject(legacy.inputStream.bufferedReader().use{it.readText()});if(discovery.optString("version")=="0.1.0")return@withContext}finally{legacy.disconnect()}
                throw IOException("Mettez à jour le serveur pour activer la connexion sécurisée.")
            }
            val info=JSONObject(c.inputStream.bufferedReader().use{it.readText()});if(!info.optBoolean("tls"))return@withContext
            val cert=CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.decode(info.getString("certificate"),Base64.DEFAULT))) as X509Certificate
            val candidate=fingerprint(cert);if(pin.isNotBlank()&&pin!=candidate)throw Exception("L’identité du serveur a changé. Vérifiez son certificat.")
            pin=candidate;configurePin();val old=URL(address);address="https://${if(old.host.contains(':'))"[${old.host}]" else old.host}:${info.getInt("port")}";prefs.edit().putString("certificatePin",pin).putString("address",address).apply()
        }finally{c.disconnect()}
    }
    private suspend fun request(route: String, data: JSONObject? = null): String = withContext(Dispatchers.IO) {
        if(data==null&&(remoteActive||vpnActive)&&System.currentTimeMillis()>nextLocalProbe){remoteActive=false;vpnActive=false;nextLocalProbe=System.currentTimeMillis()+20000}
        try { val result=requestAt(route,data,remoteActive);if(!remoteActive&&!vpnActive&&wireguard.configured)wireguard.stop();result } catch(e:IOException) {
            if(route!="pair"&&e !is javax.net.ssl.SSLException&&wireguard.configured&&!remoteActive){
                wireguard.start();vpnActive=true;nextLocalProbe=System.currentTimeMillis()+20000
                if(data!=null)throw Exception("Connexion WireGuard établie. Réessayez l’action : son résultat précédent est incertain.")
                return@withContext requestAt(route,null,false)
            }
            if(remoteUrl.isBlank()||route=="pair"||e is javax.net.ssl.SSLException)throw e
            remoteActive=!remoteActive;nextLocalProbe=System.currentTimeMillis()+20000
            if(data!=null)throw e
            requestAt(route,data,remoteActive)
        }
    }
    private fun requestAt(route: String, data: JSONObject?, remote: Boolean, method:String?=null): String {
        val connection = connection("${if(remote)remoteUrl else if(vpnActive)wireguard.serverUrl else address}/api/v1/$route",remote)
        try {
            if(method!=null)connection.requestMethod=method
            if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (data != null) { connection.requestMethod = "POST"; connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json"); connection.outputStream.use { it.write(data.toString().toByteArray()) } }
            val ok = connection.responseCode in 200..299
            val text = (if (ok) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (!ok) throw Exception(JSONObject(text).optString("message", "Serveur inaccessible"))
            return text
        } finally { connection.disconnect() }
    }
    fun publicIdentity():String {
        if (!keys.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.encodeToString(keys.getCertificate(alias).publicKey.encoded, Base64.NO_WRAP) + "\n-----END PUBLIC KEY-----"
    }
    suspend fun acceptTvApproval(data:JSONObject) {
        require(data.getString("publicKey").replace(Regex("\\s"),"")==publicIdentity().replace(Regex("\\s"),"")){"Cette autorisation appartient à une autre télévision."}
        val cert=CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.decode(data.getString("certificate"),Base64.DEFAULT))) as X509Certificate
        cert.checkValidity();pin=fingerprint(cert);configurePin()
        address=data.getString("localAddress")
        require(address.startsWith("https://") && URL(address).userInfo==null){"Adresse du serveur invalide"}
        wireguard.saveProfile(data.getJSONObject("profile"))
        prefs.edit().putString("address",address).putString("device",data.getString("deviceId")).putString("serverId",data.getString("serverId")).putString("certificatePin",pin).apply()
    }
    suspend fun usePrivateTunnel(){wireguard.start();vpnActive=true;nextLocalProbe=Long.MAX_VALUE}
    suspend fun pair(code: String) {
        secureLocal()
        val pem = publicIdentity()
        val result = JSONObject(request("pair", JSONObject().put("code", code).put("publicKey", pem).put("name", android.os.Build.MODEL)))
        prefs.edit().putString("address", address).putString("device", result.getString("deviceId")).putString("serverId",result.getString("serverId")).apply()
        authenticate()
    }
    suspend fun authenticate() {
        if(!address.startsWith("https:"))secureLocal()
        val discovery=JSONObject(request("discovery"))
        val expectedServer=prefs.getString("serverId",null)
        if(expectedServer!=null&&expectedServer!=discovery.getString("id"))throw Exception("Cette adresse ne correspond pas à votre serveur appairé.")
        if(expectedServer==null){if(remoteActive||vpnActive)throw Exception("Ouvrez Ma Maison une fois sur le Wi-Fi domestique avant d’utiliser l’accès distant.");prefs.edit().putString("serverId",discovery.getString("id")).apply()}
        val device = prefs.getString("device", null) ?: throw Exception("Appairage requis")
        val challenge = JSONObject(request("auth/challenge", JSONObject().put("deviceId", device))).getString("challenge")
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(keys.getKey(alias, null) as java.security.PrivateKey); update(challenge.toByteArray()) }.sign()
        token = JSONObject(request("auth/session", JSONObject().put("deviceId", device).put("signature", Base64.encodeToString(signature, Base64.NO_WRAP)))).getString("token")
        try { val settings=JSONObject(request("settings"));remoteUrl=settings.optString("remoteUrl");prefs.edit().putString("remoteUrl",remoteUrl).apply() }catch(_:Exception){}
    }
    suspend fun get(route: String) = request(route)
    suspend fun configureWireGuard(){
        if(pin.isBlank()||remoteActive||vpnActive)throw Exception("Configurez l’accès 5G depuis le Wi-Fi domestique, avec la connexion chiffrée au Pi.")
        val profile=JSONObject(post("wireguard/enroll",JSONObject().put("publicKey",wireguard.publicKey())))
        wireguard.saveProfile(profile)
    }
    suspend fun checkRemote():String = withContext(Dispatchers.IO){
        if(wireguard.configured)return@withContext if(vpnActive)"Accès WireGuard intégré connecté à votre serveur." else "WireGuard est configuré. Désactivez le Wi-Fi puis ouvrez Ma Maison pour vérifier la connexion 5G."
        if(remoteUrl.isBlank())throw Exception("L’accès distant n’est pas encore configuré sur le serveur.")
        val info=JSONObject(requestAt("discovery",null,true))
        val expected=prefs.getString("serverId",null)?:throw Exception("Reconnectez-vous une fois sur le Wi-Fi domestique.")
        if(info.getString("id")!=expected)throw Exception("Le relais distant ne correspond pas à votre serveur.")
        "Accès distant HTTPS vérifié pour votre serveur. Pour tester la 5G, désactivez le Wi-Fi du téléphone."
    }
    suspend fun checkServer():String {
        val info=JSONObject(request("discovery"))
        return "Serveur joignable : ${info.optString("name", "Ma Maison")} · version ${info.optString("version")}\n" + if(info.optBoolean("pairingAvailable"))"Le serveur accepte un code d’appairage." else "L’appairage est fermé : un nouveau code doit être généré sur le Pi."
    }
    suspend fun post(route: String, data: JSONObject = JSONObject()) = request(route, data)
    suspend fun delete(route:String) = withContext(Dispatchers.IO){requestAt(route,null,remoteActive,"DELETE")}
    suspend fun cameraFrame(id: String): android.graphics.Bitmap = withContext(Dispatchers.IO) {
        try { frameAt(id) } catch(e:IOException){if(e is javax.net.ssl.SSLException)throw e;if(wireguard.configured){wireguard.start();vpnActive=true}else{if(remoteUrl.isBlank())throw e;remoteActive=!remoteActive};nextLocalProbe=System.currentTimeMillis()+20000;frameAt(id)}
    }
    private fun frameAt(id:String): android.graphics.Bitmap {
        val connection = connection("${if(remoteActive)remoteUrl else if(vpnActive)wireguard.serverUrl else address}/api/v1/cameras/$id/snapshot",remoteActive)
        try {
            connection.connectTimeout = 8000; connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (connection.responseCode != 200) throw Exception(connection.errorStream.bufferedReader().use { JSONObject(it.readText()).optString("message", "Direct indisponible") })
            return connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) ?: throw Exception("Image caméra illisible") }
        } finally { connection.disconnect() }
    }
    fun mediaUrl(file: String) = "${if(remoteActive)remoteUrl else if(vpnActive)wireguard.serverUrl else address}/api/v1/files/content?path=${java.net.URLEncoder.encode(file, "UTF-8")}"
    fun authHeaders() = mapOf("Authorization" to "Bearer $token")
    fun mediaConnection(url: String): HttpURLConnection {
        val path=URL(url).file
        require(path.startsWith("/api/v1/files/content?")){"Adresse de média invalide"}
        return connection("${if(remoteActive)remoteUrl else if(vpnActive)wireguard.serverUrl else address}$path",remoteActive).apply { authHeaders().forEach{(key,value)->setRequestProperty(key,value)} }
    }
    fun mediaHttpFactory() = androidx.media3.datasource.DataSource.Factory { PinnedMediaSource(this) }
}
