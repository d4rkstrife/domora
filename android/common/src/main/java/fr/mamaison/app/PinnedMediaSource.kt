package fr.mamaison.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection

class PinnedMediaSource(private val client:MaisonClient):DataSource {
    private var connection:HttpURLConnection?=null
    private var stream:InputStream?=null
    private var uri:Uri?=null
    private var remaining:Long=C.LENGTH_UNSET.toLong()
    override fun addTransferListener(listener:TransferListener) {}
    override fun open(spec:DataSpec):Long {
        uri=spec.uri
        val c=client.mediaConnection(spec.uri.toString());connection=c
        spec.httpRequestHeaders.forEach{(key,value)->c.setRequestProperty(key,value)}
        if(spec.position>0||spec.length!=C.LENGTH_UNSET.toLong())c.setRequestProperty("Range","bytes=${spec.position}-${if(spec.length==C.LENGTH_UNSET.toLong())"" else (spec.position+spec.length-1).toString()}")
        val status=c.responseCode
        if(status !in 200..299)throw IOException("Lecture indisponible ($status)")
        if(spec.position>0&&status!=206)throw IOException("Le serveur ne permet pas cette position de lecture")
        stream=c.inputStream
        remaining=if(spec.length!=C.LENGTH_UNSET.toLong())spec.length else c.getHeaderFieldLong("Content-Length",-1)
        return remaining
    }
    override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
        if(length==0)return 0
        if(remaining==0L)return C.RESULT_END_OF_INPUT
        val count=stream?.read(buffer,offset,if(remaining<0)length else minOf(length.toLong(),remaining).toInt())?:C.RESULT_END_OF_INPUT
        if(count>0&&remaining>0)remaining-=count
        return count
    }
    override fun getUri()=uri
    override fun getResponseHeaders():Map<String,List<String>> = connection?.headerFields?.filterKeys{it!=null}?.mapKeys{it.key!!}?:emptyMap()
    override fun close(){try{stream?.close()}finally{connection?.disconnect();stream=null;connection=null;uri=null}}
}
