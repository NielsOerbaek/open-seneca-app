package org.openseneca.sensorapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

//TODO: Add better UI
//TODO: Add a notification bar, where user can see that service is running
//TODO: Add a button to kill connection in UI
//TODO: Stop/Start Service if connection drops
//TODO: What happens if connection drops? Handle this in UI

class SensorService : Service() {

    val CHANNEL_ID = "SensorServiceChannel"
    private val myBinder = MyLocalBinder()

    override fun onBind(intent : Intent) : IBinder? {
        return myBinder
    }

    inner class MyLocalBinder : Binder() {
        fun getService() : SensorService {
            return this@SensorService
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SensorService")
            .setContentText("Sending to server")
            .setContentIntent(pendingIntent)
            .build()

        Log.w("ScanDeviceActivity", "Started Service")

        startForeground(1, notification)

        return START_STICKY;
    }


    fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager!!.createNotificationChannel(serviceChannel)
    }
}

