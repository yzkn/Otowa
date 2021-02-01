package jp.gr.java_conf.ya.otowa

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*


class LoggerService : Service() {
    companion object {
        const val CHANNEL_ID = "1"
        const val ONGOING_NOTIFICATION_ID = 1

        // running status check
        const val ACTION_IS_RUNNING = "jp.gr.java_conf.ya.otowa.LoggerService_is_running"
        fun createIntent(context: Context) = Intent(context, LoggerService::class.java)
        fun isRunning(context: Context): Boolean {
            return LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(ACTION_IS_RUNNING))
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var ioUtil: IoUtil

    private var isDebugMode = false
    private var packageNameString = ""

    // running status check
    private val localBroadcastManager by lazy { LocalBroadcastManager.getInstance(applicationContext) }
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {}
    }
    //

    override fun onCreate() {
        super.onCreate()

        packageNameString = packageName.toString()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)

        // external storage
        ioUtil = IoUtil(applicationContext)

        // running status check
        localBroadcastManager.registerReceiver(broadcastReceiver, IntentFilter(ACTION_IS_RUNNING))

        // Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // val sdfFyyyyMMddHHmmss = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations){
                    if (isDebugMode) {
                        Log.d(
                            packageNameString,
                            "onStartCommand() onLocationResult() location ${location.latitude} , ${location.longitude}"
                        )
                    }

                    val strBuf = StringBuilder()

                    // strBuf.append("${location.latitude}")
                    // strBuf.append(",")
                    // strBuf.append("${location.longitude}")
                    // strBuf.append(",")
                    // strBuf.append("${location.accuracy}")
                    // strBuf.append(",")
                    // strBuf.append("${location.altitude}")
                    // strBuf.append(",")
                    // strBuf.append("${location.speed}")
                    // strBuf.append(",")
                    // strBuf.append("${location.bearing}")
                    // strBuf.append(",")
                    // strBuf.append(sdfFyyyyMMddHHmmss.format(location.time))

                    strBuf.append("${location.longitude}")
                    strBuf.append(",")
                    strBuf.append("${location.latitude}")
                    strBuf.append(",")
                    strBuf.append("${location.altitude}")

                    strBuf.append(System.getProperty("line.separator"))
                    ioUtil.saveExternalPrivateTextFile(strBuf.toString(), true)
                    //
                }
            }
        }

        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, 0)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.logging_enabled))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)

        startLocationUpdates()

        return START_STICKY
    }


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun stopService(name: Intent?): Boolean {
        stopLocationUpdates()

        return super.stopService(name)
    }

    override fun onDestroy() {
        // Location
        stopLocationUpdates()
        stopSelf()

        // running status check
        localBroadcastManager.unregisterReceiver(broadcastReceiver)

        super.onDestroy()
    }

    private fun startLocationUpdates() {
        val locationRequest = createLocationRequest() ?: return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createLocationRequest(): LocationRequest? {
        return LocationRequest.create()?.apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
}