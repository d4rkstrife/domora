package fr.domora.tv

import fr.mamaison.app.TvLink
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest

class PairingReceiver : AutoCloseable {
 private var server:ServerSocket?=null
 override fun close(){runCatching{server?.close()}}
 suspend fun await(request:JSONObject):JSONObject=withContext(Dispatchers.IO){
  val socket=ServerSocket(8846).also{server=it;it.soTimeout=1000}
  try {
   var attempts=0
   while(System.currentTimeMillis()<request.getLong("expires")){
    ensureActive()
    val client=try{socket.accept()}catch(_:SocketTimeoutException){continue}
    client.use { c ->
     c.soTimeout=4000
     try {
      require(++attempts<=64){"Trop de tentatives. Relancez le QR code."}
      val input=c.getInputStream()
      fun line():String {val out=java.io.ByteArrayOutputStream();while(true){val b=input.read();require(b>=0);if(b==10)break;require(out.size()<2048);out.write(b)};return out.toString("UTF-8").trimEnd('\r')}
      require(line()=="POST /approve HTTP/1.1")
      var length=-1;var headers=0
      while(true){val h=line();if(h.isEmpty())break;require(++headers<40);if(h.startsWith("Content-Length:",true))length=h.substringAfter(':').trim().toInt()}
      require(length in 1..16000)
      val bytes=ByteArray(length);var offset=0
      while(offset<length){val count=input.read(bytes,offset,length-offset);require(count>0);offset+=count}
      val packet=JSONObject(String(bytes,Charsets.UTF_8));val body=packet.getString("body")
      require(System.currentTimeMillis()<request.getLong("expires"))
      require(MessageDigest.isEqual(TvLink.mac(request.getString("secret"),body).toByteArray(),packet.getString("mac").toByteArray()))
      val approval=JSONObject(body)
      require(approval.getString("publicKey").filterNot{it.isWhitespace()}==request.getString("publicKey").filterNot{it.isWhitespace()})
      c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
      return@withContext approval
     }catch(e:Exception){if(attempts>64)throw e;runCatching{c.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())}}
    }
   }
   throw Exception("QR code expiré. Relancez l’appairage.")
  }finally{socket.close();server=null}
  @Suppress("UNREACHABLE_CODE") JSONObject()
 }
}
