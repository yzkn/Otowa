// Copyright (c) 2023 YA-androidapp(https://github.com/YA-androidapp) All rights reserved.
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
import androidx.core.app.RemoteInput
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class LoggerService : Service() {
    companion object {
        const val INPUT_TWEET = "INPUT_TWEET"
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
    private var isDebugModeLoop = false
    private var packageNameString = ""

    private val INTERVAL_MS_FAST = 1_000L
    private val INTERVAL_MS_SLOW = INTERVAL_MS_FAST * 6
    private val INTERVAL_METERS_FAST = 1F
    private val INTERVAL_METERS_SLOW = INTERVAL_METERS_FAST * 6

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
        isDebugModeLoop = sharedPreferences.getBoolean("pref_is_debug_mode_loop", false)

        // external storage
        ioUtil = IoUtil(applicationContext)

        // running status check
        localBroadcastManager.registerReceiver(broadcastReceiver, IntentFilter(ACTION_IS_RUNNING))

        // Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private var preLine = ""
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ISO8601
        val tzUtc: TimeZone = TimeZone.getTimeZone("UTC")
        val dfIso: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.JAPAN)
        dfIso.timeZone = tzUtc

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    try {
                        val strBuf = StringBuilder()

                        if (inHomeArea(location)) {
                            if (isDebugMode && isDebugModeLoop) {
                                Log.v(
                                    packageNameString,
                                    "onStartCommand() onLocationResult() inHomeArea(location) location ${location.latitude} , ${location.longitude}"
                                )
                            }

                            if ("" != preLine && System.getProperty("line.separator") != preLine) {
                                strBuf.append(System.getProperty("line.separator"))
                                // 前の行も空行なら追加しない
                            }
                        } else {
                            // km/h
                            val speedString =
                                if (location.speed >= 10) "%03.1f".format(location.speed * 3600 / 1000) else "000.0"

                            if (isDebugMode && isDebugModeLoop) {
                                Log.v(
                                    packageNameString,
                                    "onStartCommand() onLocationResult() !inHomeArea(location) location ${location.latitude} , ${location.longitude} ${location.altitude} $speedString"
                                )
                            }

                            strBuf.append("${location.longitude}")
                            strBuf.append(",")
                            strBuf.append("${location.latitude}")
                            strBuf.append(",")
                            strBuf.append("${location.altitude}")
                            strBuf.append(",")
                            strBuf.append(speedString)
                            strBuf.append(",")
                            strBuf.append(dfIso.format(Date()))
                            strBuf.append(System.getProperty("line.separator"))
                        }

                        ioUtil.saveExternalPrivateTextFile(strBuf.toString(), true)
                        preLine = strBuf.toString()
                    } catch (e: Exception) {
                        if (isDebugMode) {
                            Log.e(packageNameString, "onStartCommand() onLocationResult() $e")
                        }
                    }
                }
            }
        }

        //
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, 0)
        }
        //
        val locatePendingIntent = Intent("jp.gr.java_conf.ya.otowa.NOTIFICATION_LOCATE").let {
            PendingIntent.getBroadcast(this, 0, it, 0)
        }
        //
        val remoteIntent = Intent("jp.gr.java_conf.ya.otowa.NOTIFICATION_REMOTE").let {
            PendingIntent.getBroadcast(this, 0, it, 0)
        }
        val remoteInput = RemoteInput.Builder(INPUT_TWEET).run {
            setLabel(getString(R.string.tweet))
            build()
        }
        val remoteAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            getString(R.string.tweet_from_remote),
            remoteIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .addAction(
                android.R.drawable.ic_menu_mylocation,
                getString(R.string.tweet_from_notification),
                locatePendingIntent
            )
            .addAction(remoteAction)
            .setContentIntent(openIntent)
            .setContentText(getString(R.string.logging_enabled))
            .setContentTitle(getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.mipmap.ic_launcher)
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
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val locatingIntervalMillisecondsString =
            sharedPreferences.getString(
                "pref_locating_interval_milliseconds",
                getString(R.string.locating_interval_milliseconds_default)
            ) ?: getString(R.string.locating_interval_milliseconds_default)
        val locatingIntervalMetersString =
            sharedPreferences.getString(
                "pref_locating_interval_meters",
                getString(R.string.locating_interval_meters_default)
            ) ?: getString(R.string.locating_interval_meters_default)

        var locatingIntervalMilliseconds = 10_000L
        if (locatingIntervalMillisecondsString != "") {
            try {
                locatingIntervalMilliseconds = locatingIntervalMillisecondsString.toLong()
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.e(
                        packageNameString,
                        "LoggerService createLocationRequest() locatingIntervalMillisecondsString: $locatingIntervalMillisecondsString $e"
                    )
                }
            }
        }
        var locatingIntervalMeters = 5F
        if (locatingIntervalMetersString != "") {
            try {
                locatingIntervalMeters = (locatingIntervalMetersString + "F").toFloat()
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.e(
                        packageNameString,
                        "LoggerService createLocationRequest() locatingIntervalMetersString: $locatingIntervalMetersString $e"
                    )
                }
            }
        }

        if (isDebugMode) {
            Log.v(
                packageNameString,
                "LoggerService createLocationRequest() locatingIntervalMeters: $locatingIntervalMeters locatingIntervalMilliseconds: $locatingIntervalMilliseconds"
            )
        }

        return LocationRequest.create().apply {
            interval = locatingIntervalMilliseconds
            fastestInterval = locatingIntervalMilliseconds
            smallestDisplacement = locatingIntervalMeters
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
            if (isDebugMode && isDebugModeLoop) {
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
                if (isDebugMode && isDebugModeLoop) {
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
                            if (isDebugMode && isDebugModeLoop) {
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
            if (isDebugMode && isDebugModeLoop) {
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