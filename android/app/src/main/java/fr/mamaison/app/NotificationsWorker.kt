package fr.mamaison.app

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

class NotificationsWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    private fun notify(message:String,id:Int){
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("maison-status","Votre maison",NotificationManager.IMPORTANCE_DEFAULT))
        if(android.os.Build.VERSION.SDK_INT>=33&&applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        manager.notify(id,Notification.Builder(applicationContext,"maison-status").setSmallIcon(fr.mamaison.app.R.drawable.ic_maison).setContentTitle("Ma Maison").setContentText(message).setAutoCancel(true).build())
    }
    override suspend fun doWork():Result {
        val client=MaisonClient(applicationContext);if(!client.paired)return Result.success()
        val prefs=applicationContext.getSharedPreferences("maison-notifications",Context.MODE_PRIVATE)
        try {
            client.authenticate();val settings=JSONObject(client.get("settings"));val enabled=settings.optJSONObject("notifications")?:JSONObject();val events=JSONArray(client.get("events"));val previous=prefs.getString("lastEvent",null)
            if(previous!=null){for(i in 0 until events.length()){val e=events.getJSONObject(i);if(e.getString("id")==previous)break;if(enabled.optBoolean(e.getString("type")))notify(e.getString("message"),e.getString("id").hashCode())}}
            if(events.length()>0)prefs.edit().putString("lastEvent",events.getJSONObject(0).getString("id")).apply()
            prefs.edit().putBoolean("connectionFailed",false).apply()
        }catch(e:CancellationException){throw e}catch(_:Exception){if(!prefs.getBoolean("connectionFailed",false)){notify("Impossible de joindre votre serveur actuellement.",100);prefs.edit().putBoolean("connectionFailed",true).apply()}}
        return Result.success()
    }
    companion object {
        fun enable(context:Context){val work=PeriodicWorkRequestBuilder<NotificationsWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("maison-status",ExistingPeriodicWorkPolicy.KEEP,work)}
    }
}
