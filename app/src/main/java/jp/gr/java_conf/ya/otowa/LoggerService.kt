package jp.gr.java_conf.ya.otowa

import android.Manifest
import android.annotation.SuppressLint
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


class LoggerService : Service() {
    companion object {
        const val CHANNEL_ID = "1"
        const val ONGOING_NOTIFICATION_ID = 1

        // running status check
        const val ACTION_IS_RUNNING = "jp.gr.java_conf.ya.otowa.LoggerService_is_running"
        fun createIntent(context: Context) = Intent(context, LoggerService::class.java)
        fun isRunning(context: Context): Boolean {
            return LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent(ACTION_IS_RUNNING))
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
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    try {
                        if (inHomeArea(location)) {
                            if (isDebugMode) {
                                Log.d(
                                    packageNameString,
                                    "onStartCommand() onLocationResult() inHomeArea(location) location ${location.latitude} , ${location.longitude}"
                                )
                            }
                        }else{
                            if (isDebugMode) {
                                Log.d(
                                    packageNameString,
                                    "onStartCommand() onLocationResult() !inHomeArea(location) location ${location.latitude} , ${location.longitude}"
                                )
                            }

                            val strBuf = StringBuilder()

                            // val sdfFyyyyMMddHHmmss = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
                            // strBuf.append("${location.latitude}")
                            // strBuf.append("${location.longitude}")
                            // strBuf.append("${location.accuracy}")
                            // strBuf.append("${location.altitude}")
                            // strBuf.append("${location.speed}")
                            // strBuf.append("${location.bearing}")
                            // strBuf.append(sdfFyyyyMMddHHmmss.format(location.time))

                            strBuf.append("${location.longitude}")
                            strBuf.append(",")
                            strBuf.append("${location.latitude}")
                            strBuf.append(",")
                            strBuf.append("${location.altitude}")

                            strBuf.append(System.getProperty("line.separator"))
                            ioUtil.saveExternalPrivateTextFile(strBuf.toString(), true)
                        }
                    } catch (e: Exception) {
                        if (isDebugMode) {
                            Log.e(packageNameString, "onStartCommand() onLocationResult() $e")
                        }
                    }
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

    @SuppressLint("MissingPermission")
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
                null
            )
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


    private fun inHomeArea(location: android.location.Location): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val homeAreaLatitudeString =
            sharedPreferences.getString("pref_home_area_latitude", "") ?: ""
        val homeAreaLongitudeString =
            sharedPreferences.getString("pref_home_area_longitude", "") ?: ""
        val homeAreaRadiusString = sharedPreferences.getString("pref_home_area_radius", "") ?: ""

        if (homeAreaLatitudeString != "" && homeAreaLongitudeString != "" && homeAreaRadiusString != "") {
            if (isDebugMode) {
                Log.v(
                    packageNameString, "LoggerService inHomeArea() !empty:"
                            + " homeAreaLatitudeString: $homeAreaLatitudeString"
                            + " homeAreaLongitudeString: $homeAreaLongitudeString"
                            + " homeAreaRadiusString: $homeAreaRadiusString"
                )
            }

            try {
                val homeAreaLatitude = java.lang.Double.parseDouble(homeAreaLatitudeString)
                val homeAreaLongitude = java.lang.Double.parseDouble(homeAreaLongitudeString)
                val homeAreaRadius = java.lang.Double.parseDouble(homeAreaRadiusString)
                if (isDebugMode) {
                    Log.v(
                        packageNameString, "LoggerService inHomeArea() parsed:"
                                + " homeAreaLatitude: $homeAreaLatitude"
                                + " homeAreaLongitude: $homeAreaLongitude"
                                + " homeAreaRadius: $homeAreaRadius"
                    )
                }

                val results = floatArrayOf(0F, 0F, 0F)
                try {
                    android.location.Location.distanceBetween(
                        location.latitude,
                        location.longitude,
                        homeAreaLatitude,
                        homeAreaLongitude,
                        results
                    )
                    if (results.isNotEmpty()) {
                        val distance = results[0].toDouble()
                        if (distance < homeAreaRadius) {
                            if (isDebugMode) {
                                Log.v(
                                    packageNameString,
                                    "LoggerService inHomeArea() home-area:"
                                            + " distance: $distance"
                                            + " homeAreaRadius: $homeAreaRadius"
                                )
                            }
                            return true
                        }
                    }
                } catch (e: Exception) {
                    if (isDebugMode) {
                        Log.e(packageNameString, "LoggerService inHomeArea() $e")
                    }
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.e(
                        packageNameString, "LoggerService inHomeArea()"
                                + " homeAreaLatitudeString: $homeAreaLatitudeString"
                                + " homeAreaLongitudeString: $homeAreaLongitudeString"
                                + " homeAreaRadiusString: $homeAreaRadiusString $e"
                    )
                }
            }
        } else {
            // 未設定の場合
            if (isDebugMode) {
                Log.v(
                    packageNameString, "LoggerService inHomeArea() empty"
                            + " homeAreaLatitudeString: $homeAreaLatitudeString"
                            + " homeAreaLongitudeString: $homeAreaLongitudeString"
                            + " homeAreaRadiusString: $homeAreaRadiusString"
                )
            }
        }

        return false
    }
}