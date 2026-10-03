package fr.mamaison.app

import android.content.Context
import android.net.VpnService
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MaisonWireGuard private constructor(private val context:Context){
    private val prefs=context.getSharedPreferences("maison-wireguard",Context.MODE_PRIVATE)
    private val lock=Mutex()
    private val backend by lazy{GoBackend(context)}
    private val tunnel=object:Tunnel{
        override fun getName()="MaMaison"
        override fun onStateChange(state:Tunnel.State){}
    }
    val configured get()=prefs.contains("profile")&&prefs.getBoolean("enabled",false)
    val serverUrl get()="https://10.203.77.1:8789"
    fun permissionIntent()=VpnService.prepare(context)
    private fun storageKey():SecretKey{
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(!store.containsAlias("maison-wireguard-storage"))KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("maison-wireguard-storage",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
        return store.getKey("maison-wireguard-storage",null) as SecretKey
    }
    private fun privateKey():String{
        val encrypted=prefs.getString("privateKey",null)
        if(encrypted!=null){val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,storageKey(),GCMParameterSpec(128,Base64.decode(prefs.getString("iv","")!!,Base64.NO_WRAP)));return String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),Charsets.UTF_8)}
        val key=KeyPair().privateKey.toBase64();val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,storageKey());prefs.edit().putString("privateKey",Base64.encodeToString(cipher.doFinal(key.toByteArray()),Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).commit();return key
    }
    suspend fun publicKey()=withContext(Dispatchers.IO){lock.withLock{KeyPair(Key.fromBase64(privateKey())).publicKey.toBase64()}}
    suspend fun saveProfile(profile:JSONObject)=withContext(Dispatchers.IO){lock.withLock{
        require(profile.getString("serverAddress")=="10.203.77.1")
        require(Regex("10\\.203\\.77\\.[0-9]{1,3}/32").matches(profile.getString("address")))
        Key.fromBase64(profile.getString("publicKey"))
        require(profile.getString("address").substringAfterLast('.').substringBefore('/').toInt() in 2..254)
        com.wireguard.config.InetEndpoint.parse(profile.getString("endpoint"))
        prefs.edit().putString("profile",profile.toString()).putBoolean("enabled",true).commit()
    }}
    suspend fun start()=withContext(Dispatchers.IO){lock.withLock{
        if(!configured)throw Exception("L’accès WireGuard n’est pas encore configuré.")
        if(permissionIntent()!=null)throw Exception("Autorisez l’accès 5G intégré dans Plus, sur le Wi-Fi domestique.")
        if(backend.getState(tunnel)==Tunnel.State.UP)return@withLock
        val profile=JSONObject(prefs.getString("profile",null)!!)
        val text="[Interface]\nPrivateKey = ${privateKey()}\nAddress = ${profile.getString("address")}\nIncludedApplications = ${context.packageName}\nMTU = 1280\n\n[Peer]\nPublicKey = ${profile.getString("publicKey")}\nAllowedIPs = 10.203.77.1/32\nEndpoint = ${profile.getString("endpoint")}\nPersistentKeepalive = 25\n"
        backend.setState(tunnel,Tunnel.State.UP,Config.parse(text.byteInputStream()))
    }}
    suspend fun stop()=withContext(Dispatchers.IO){lock.withLock{if(prefs.contains("profile"))backend.setState(tunnel,Tunnel.State.DOWN,null)}}
    suspend fun disable(){stop();prefs.edit().putBoolean("enabled",false).apply()}
    companion object{
        @Volatile private var instance:MaisonWireGuard?=null
        fun get(context:Context):MaisonWireGuard=instance?:synchronized(this){instance?:MaisonWireGuard(context.applicationContext).also{instance=it}}
    }
}
