// Copyright (c) 2021 YA-androidapp(https://github.com/YA-androidapp) All rights reserved.
package jp.gr.java_conf.ya.otowa

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.*
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.RemoteInput
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.beust.klaxon.Klaxon
import com.github.twocoffeesoneteam.glidetovectoryou.GlideToVectorYou
import com.google.android.gms.location.*
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.squareup.picasso.Picasso
import jp.gr.java_conf.ya.otowa.forecast.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.Double.parseDouble
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
        var instance: MainActivity? = null
    }

    private lateinit var cityDbController: AppDBController
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var imageWeather: ImageView
    private lateinit var locationCallback: LocationCallback
    private lateinit var textViewWeather: TextView

    private var areas: Array<WeatherArea> = emptyArray()
    private var isDebugMode = false
    private var isDebugModeLoop = false
    private var isLocationUpdatesStarted = false
    private var packageNameString = ""
    private var updatedCount = 0

    private val INTERVAL_MILLISECONDS = 1_000L
    private val INTERVAL_METERS = 0F
    private val WRITE_REQUEST_CODE = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // 設定
        packageNameString = packageName.toString()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)
        isDebugModeLoop = sharedPreferences.getBoolean("pref_is_debug_mode_loop", false)
        with(sharedPreferences.edit()) {
            putString("pref_current_latitude", "")
            putString("pref_current_longitude", "")
            commit()
        }

        // 通知
        instance = this
        this.applicationContext.registerReceiver(
            NotificationClickedBroadcastReceiver(),
            IntentFilter("jp.gr.java_conf.ya.otowa.NOTIFICATION_LOCATE")
        )
        this.applicationContext.registerReceiver(
            NotificationRemoteBroadcastReceiver(),
            IntentFilter("jp.gr.java_conf.ya.otowa.NOTIFICATION_REMOTE")
        )
        createNotificationChannel()

        // CSVの読み取り
        readCsv()

        // 接続状況
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // 測位
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                locate(locationResult)
            }
        }

        initDb()
        checkPackages()
    }

    private fun checkPackages() {
        val pm: PackageManager = getPackageManager()
        val pckInfoList =
            pm.getInstalledPackages(PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
        for (pckInfo in pckInfoList) {
            if (pm.getLaunchIntentForPackage(pckInfo.packageName) != null) {
                val packageName = pckInfo.packageName
                val className =
                    pm.getLaunchIntentForPackage(pckInfo.packageName)!!.component!!
                        .className + ""
                Log.i(packageNameString, packageName + "\t" + className)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            LoggerService.CHANNEL_ID,
            getStr(R.string.logger),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getStr(R.string.notification_description_logger)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun readCsv() {
        try {
            val file = resources.assets.open("area.csv")
            val fileReader = BufferedReader(InputStreamReader(file))
            var i: Int = 0
            fileReader.forEachLine {
                if (it.isNotBlank()) {
                    if (i > 0) {
                        // ヘッダ行をスキップ
                        val line = it.split(",").toTypedArray()
                        fetchCSV(line)
                    }
                }
                i++
            }

        } catch (e: IOException) {
            if (isDebugMode) {
                Log.e(packageNameString, "readCsv() $e")
            }
        }
    }

    private fun fetchCSV(line: Array<String>) {
        val area = WeatherArea(
            id = line[0],
            city = line[1],
            lat = line[2].toDoubleOrNull(),
            lng = line[3].toDoubleOrNull()
        )
        areas += area
        if (isDebugMode) {
            Log.v(packageNameString, "readCsv() area: $area")
        }
    }

    private fun locate(locationResult: LocationResult) {
        for (location in locationResult.locations) {
            try {
                if (isDebugMode && isDebugModeLoop) {
                    Log.v(packageNameString, "locate() location")
                }

                updatedCount++

                if (inHomeArea(location)) {
                    if (isDebugMode && isDebugModeLoop) {
                        Log.v(
                            packageNameString,
                            "locate() $updatedCount ${location.latitude} , ${location.longitude} inHomeArea"
                        )
                    }

                    with(
                        PreferenceManager.getDefaultSharedPreferences(application).edit()
                    ) {
                        putString("pref_current_latitude", "")
                        putString("pref_current_longitude", "")

                        commit()
                    }

                    // 測位ボタンを無効化する
                    updateLocateButton()
                } else {
                    if (isDebugMode && isDebugModeLoop) {
                        Log.v(
                            packageNameString,
                            "onCreate() $updatedCount ${location.latitude} , ${location.longitude} !inHomeArea"
                        )
                    }

                    with(
                        PreferenceManager.getDefaultSharedPreferences(application).edit()
                    ) {
                        putString("pref_current_latitude", location.latitude.toString())
                        putString("pref_current_longitude", location.longitude.toString())

                        commit()
                    }

                    // 測位ボタンを有効化する
                    updateLocateButton(location.latitude, location.longitude)
                }

                // 測位ごとに毎回行う
                try {
                    if (isDebugMode && isDebugModeLoop) {
                        Log.v(packageNameString, "locate() 測位ごとに毎回行う")
                    }

                    setLocationViewVisibility(location.hasSpeed() && ((location.speed * 3600 / 1000) > 80) && location.hasAltitude() && location.hasBearing())
                    updateClock(location.time)
                    if (location.hasAltitude()) {
                        updateAltimeter(location.altitude)
                    }
                    if (location.hasBearing()) {
                        updateCompass(location.bearing, location.speed)
                    }
                    if (location.hasSpeed()) {
                        updateSpeedMeter(location.speed)
                    }
                } catch (e: java.lang.Exception) {
                    if (isDebugMode) {
                        Log.e(packageNameString, "locate() 測位ごとに毎回行う $e")
                    }
                }

                // たまに行う
                if (1 == (updatedCount % 1000)) {
                    try {
                        if (isDebugMode && isDebugModeLoop) {
                            Log.v(packageNameString, "locate() たまに行う")
                        }

                        updateSunriseSunsetLabel(location.latitude, location.longitude)

                        updateLoggingButton()
                    } catch (e: java.lang.Exception) {
                        if (isDebugMode) {
                            Log.e(packageNameString, "locate() たまに行う $e")
                        }
                    }
                }

                // ごくまれに行う
                if (1 == (updatedCount % 100000)) {
                    try {
                        if (isDebugMode && isDebugModeLoop) {
                            Log.v(packageNameString, "locate() ごくまれに行う")
                        }

                        updateWeatherForecastLabel(location.latitude, location.longitude)
                    } catch (e: java.lang.Exception) {
                        if (isDebugMode) {
                            Log.e(packageNameString, "locate() ごくまれに行う $e")
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                if (isDebugMode) {
                    Log.e(packageNameString, "locate() for $e")
                }
            }
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
                    packageNameString, "inHomeArea() !empty:"
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
                        packageNameString, "inHomeArea() parsed:"
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
                                    "inHomeArea() home-area:"
                                            + " distance: $distance"
                                            + " homeAreaRadius: $homeAreaRadius"
                                )
                            }
                            return true
                        }
                    }
                } catch (e: Exception) {
                    if (isDebugMode) {
                        Log.e(packageNameString, "inHomeArea() $e")
                    }
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.e(
                        packageNameString, "inHomeArea()"
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
                    packageNameString, "inHomeArea() empty"
                            + " homeAreaLatitudeString: $homeAreaLatitudeString"
                            + " homeAreaLongitudeString: $homeAreaLongitudeString"
                            + " homeAreaRadiusString: $homeAreaRadiusString"
                )
            }
        }

        return false
    }

    private fun setLocationViewVisibility(cond: Boolean) {
        val linearLayoutLocation = findViewById<LinearLayout>(R.id.linear_layout_location)
        val linearLayoutWebview = findViewById<LinearLayout>(R.id.linear_layout_webview)
        if (cond) {
            linearLayoutLocation.visibility = View.VISIBLE
            linearLayoutWebview.visibility = View.GONE
        } else {
            linearLayoutLocation.visibility = View.GONE
            linearLayoutWebview.visibility = View.VISIBLE
        }
    }

    private fun updateClock(utcTime: Long) {
        val textviewClock = findViewById<TextView>(R.id.textview_clock)

        if (textviewClock != null) {
            val unixDate = Date(utcTime)
            val sdf = SimpleDateFormat("HH:mm")
            sdf.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            textviewClock.text = sdf.format(unixDate)
        }
    }

    private fun updateLocateButton() {
        // ボタンを無効化する
        val buttonLocate = findViewById<Button>(R.id.button_locate)
        if (buttonLocate != null) {
            buttonLocate.isEnabled = false
            buttonLocate.textSize = 14F
            buttonLocate.text = getStr(R.string.locate)
        }

        val buttonTweetLocation = findViewById<Button>(R.id.button_tweet_location)
        if (buttonTweetLocation != null) {
            buttonTweetLocation.isEnabled = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLocateButton(latitude: Double, longitude: Double) {
        // 測位に成功したらボタンのテキストを更新する
        val buttonLocate = findViewById<Button>(R.id.button_locate)
        if (buttonLocate != null) {
            buttonLocate.isEnabled = true
            buttonLocate.textSize = 8F

            // 逆ジオコーディング
            if (::cityDbController.isInitialized) {
                val searched =
                    cityDbController.searchCity(latitude, longitude)
                if (isDebugMode && isDebugModeLoop) {
                    Log.v(
                        packageNameString,
                        "updateLocateButton() searched: ${searched?.first} ${searched?.second}"
                    )
                }

                if (!searched?.first.isNullOrEmpty() && !searched?.second.isNullOrEmpty()) {
                    buttonLocate.text =
                        getStr(R.string.locate) + "\n" + searched?.first + "\n" + searched?.second
                } else {
                    buttonLocate.text = getStr(R.string.locate)
                }
            } else {
                buttonLocate.text = getStr(R.string.locate)
            }
        }

        val buttonTweetLocation = findViewById<Button>(R.id.button_tweet_location)
        if (buttonTweetLocation != null) {
            buttonTweetLocation.isEnabled = true
        }
    }

    private fun updateWeatherForecastLabel(latitude: Double, longitude: Double) {
        if (isDebugMode) {
            Log.v(
                packageNameString,
                "updateWeatherForecastLabel() latitude: $latitude longitude: $longitude"
            )
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val weatherForecastBaseUrl =
            sharedPreferences.getString(
                "pref_weather_forecast_base_url",
                getStr(R.string.weather_forecast_base_url_default)
            ) ?: getStr(R.string.weather_forecast_base_url_default)

        // 最も近い天気予報エリアを取得
        var minDistance = 1_000_000_000.0
        var nearestArea: WeatherArea? = null
        for (area in areas) {
            if (area.id != null && area.city != null && area.lat != null && area.lng != null) {
                val results = floatArrayOf(0F, 0F, 0F)
                try {
                    android.location.Location.distanceBetween(
                        latitude,
                        longitude,
                        area.lat!!,
                        area.lng!!,
                        results
                    )
                    if (results.isNotEmpty()) {
                        val distance = results[0].toDouble()
                        if (isDebugMode && isDebugModeLoop) {
                            Log.v(
                                packageNameString,
                                "updateWeatherReportLabel() city: ${area.city} distance: $distance"
                            )
                        }
                        if (distance <= minDistance) {
                            minDistance = distance
                            nearestArea = area
                            if (isDebugMode && isDebugModeLoop) {
                                Log.v(
                                    packageNameString,
                                    "updateWeatherReportLabel() NearestArea city: ${area.city} distance: $distance minDistance: $minDistance"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isDebugMode) {
                        Log.e(packageNameString, "updateWeatherForecastLabel() $e")
                    }
                }
            }
        }

        // 最も近い天気予報エリアの予報内容を取得
        if (nearestArea != null) {
            if (weatherForecastBaseUrl != "") {
                val url = weatherForecastBaseUrl + nearestArea.id

                if (isDebugMode) {
                    Log.v(
                        packageNameString,
                        "updateWeatherForecastLabel() nearestArea: $nearestArea url: $url"
                    )
                }

                if (url != "") {
                    try {
                        val client = OkHttpClient()
                        val request = Request.Builder().url(url).build()

                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                if (isDebugMode) {
                                    Log.e(
                                        packageNameString,
                                        "updateWeatherForecastLabel() onFailure() $e"
                                    )
                                }
                            }

                            override fun onResponse(call: Call, response: Response) {
                                if (!response.isSuccessful) {
                                    if (isDebugMode) {
                                        Log.e(
                                            packageNameString,
                                            "updateWeatherForecastLabel() !response.isSuccessful"
                                        )
                                    }
                                    return
                                }

                                val json = response.body!!.string()
                                if (isDebugMode) {
                                    Log.v(
                                        packageNameString,
                                        json
                                    )
                                }

                                val forecast = Klaxon().parse<WeatherForecast>(json)

                                if (isDebugMode) {
                                    Log.v(
                                        packageNameString,
                                        "updateWeatherForecastLabel() forecast: $forecast"
                                    )
                                }

                                if (forecast != null) {
                                    if ((forecast.forecasts)?.isNotEmpty() == true) {
                                        if (isDebugMode) {
                                            Log.v(
                                                packageNameString,
                                                "updateWeatherForecastLabel() forecast.forecasts[0].telop: ${forecast.forecasts[0].telop}"
                                            )
                                            Log.v(
                                                packageNameString,
                                                "updateWeatherForecastLabel() forecast.forecasts[0].image.url: ${forecast.forecasts[0].image?.url}"
                                            )
                                        }

                                        try {
                                            lifecycleScope.launch(Dispatchers.Default) {
                                                withContext(Dispatchers.Main) {
                                                    // アイコンを設定
                                                    if (forecast.forecasts[0].image?.url != null) {
                                                        if (!::imageWeather.isInitialized) {
                                                            imageWeather =
                                                                findViewById<ImageButton>(R.id.image_weather)
                                                        }

                                                        if (forecast.forecasts[0].image!!.url!!.endsWith(
                                                                "png"
                                                            )
                                                        ) {
                                                            Picasso.get()
                                                                .load(forecast.forecasts[0].image!!.url)
                                                                .into(imageWeather)
                                                        } else {
                                                            GlideToVectorYou
                                                                .init()
                                                                .with(applicationContext)
                                                                .load(
                                                                    Uri.parse(forecast.forecasts[0].image!!.url),
                                                                    imageWeather
                                                                )
                                                        }
                                                    }

                                                    // ラベルを設定
                                                    if (!::textViewWeather.isInitialized) {
                                                        textViewWeather =
                                                            findViewById<TextView>(R.id.textview_weather)
                                                    }
                                                    textViewWeather.text =
                                                        forecast.forecasts[0].telop
                                                }
                                            }
                                        } catch (e: Exception) {
                                            if (isDebugMode) {
                                                Log.e(
                                                    packageNameString,
                                                    "updateWeatherForecastLabel() lifecycleScope.launch(Dispatchers.Default) $e"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        })

                        client.dispatcher.executorService.shutdown()
                    } catch (e: Exception) {
                        if (isDebugMode) {
                            Log.e(
                                packageNameString,
                                "updateWeatherForecastLabel() (url != \"\") $e"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateSunriseSunsetLabel(latitude: Double, longitude: Double) {
        if (isDebugMode) {
            Log.v(
                packageNameString,
                "updateSunriseSunsetLabel() latitude: $latitude longitude: $longitude"
            )
        }

        val ssPair = sunriseSunsetCalc(latitude, longitude)
        if (isDebugMode) {
            Log.v(
                packageNameString,
                "updateSunriseSunsetLabel() ssPair: ${ssPair.first}, ${ssPair.second}"
            )
        }

        val textviewSunrize = findViewById<TextView>(R.id.textview_sunrise)
        val textviewSunset = findViewById<TextView>(R.id.textview_sunset)
        if (textviewSunrize != null) {
            textviewSunrize.text = ssPair.first
        }
        if (textviewSunset != null) {
            textviewSunset.text = ssPair.second
        }
    }

    private fun updateCompass(bearing: Float, speed: Float) {
        if (isDebugMode && isDebugModeLoop) {
            Log.v(packageNameString, "updateCompass() bearing: $bearing")
        }

        val textviewCompass = findViewById<TextView>(R.id.textview_compass)
        if (textviewCompass != null) {
            textviewCompass.text = getStr(R.string.compass_symbol)
            textviewCompass.rotation = bearing
        }

        val textviewCompassDirection = findViewById<TextView>(R.id.textview_compass_direction)
        if (textviewCompassDirection != null) {
            textviewCompassDirection.text = bearing2direction(bearing)
        }

        if (speed >= 10) {
            textviewCompass.visibility = View.VISIBLE
            textviewCompassDirection.visibility = View.VISIBLE
        } else {
            textviewCompass.visibility = View.INVISIBLE
            textviewCompassDirection.visibility = View.INVISIBLE
        }
    }

    private fun bearing2direction(bearing: Float): String {
        when {
            67.5 > bearing && bearing > 22.5 -> {
                return "NE"
            }
            112.5 > bearing && bearing > 67.5 -> {
                return "E"
            }
            157.5 > bearing && bearing > 112.5 -> {
                return "SE"
            }
            202.5 > bearing && bearing > 157.5 -> {
                return "S"
            }
            247.5 > bearing && bearing > 202.5 -> {
                return "SW"
            }
            292.5 > bearing && bearing > 247.5 -> {
                return "W"
            }
            337.5 > bearing && bearing > 292.5 -> {
                return "NW"
            }
            else -> {
                return "N"
            }
        }
    }

    private fun updateAltimeter(altitude: Double) {
        if (isDebugMode && isDebugModeLoop) {
            Log.v(packageNameString, "updateAltimeter() altitude: $altitude")
        }

        val textviewAltimeter = findViewById<TextView>(R.id.textview_altimeter)
        if (textviewAltimeter != null) {
            var altitudeString = getStr(R.string.all_zero_altimeter)
            try {
                altitudeString = "%04.0f".format(altitude) + getStr(R.string.altimeter_m)
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.v(packageNameString, "updateAltimeter() e: $e")
                }
            }
            textviewAltimeter.text = altitudeString
        }
    }

    private fun updateSpeedMeter(speed: Float) {
        if (isDebugMode && isDebugModeLoop) {
            Log.v(packageNameString, "updateSpeedMeter() speed: $speed")
        }

        val textviewSpeed = findViewById<TextView>(R.id.textview_speed)
        val textviewSpeedLabel = findViewById<TextView>(R.id.textview_speed_label)

        if (textviewSpeed != null) {
            val speedKmh = speed * 3600 / 1000
            var speedKmhString = "!!!"
            try {
                speedKmhString = "%03.0f".format(speedKmh) // m/s => km/h
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.v(packageNameString, "updateSpeedMeter() e: $e")
                }
            }
            textviewSpeed.text = speedKmhString

            if (speedKmh >= 10) {
                textviewSpeed.visibility = View.VISIBLE
                textviewSpeedLabel.visibility = View.VISIBLE
            } else {
                textviewSpeed.visibility = View.INVISIBLE
                textviewSpeedLabel.visibility = View.INVISIBLE
            }

            when {
                speedKmh > 120 -> {
                    textviewSpeed.setTextColor(Color.RED)
                }
                speedKmh > 80 -> {
                    textviewSpeed.setTextColor(Color.rgb(255, 128, 0))
                }
                else -> {
                    textviewSpeed.setTextColor(Color.BLACK)
                }
            }
        }
    }

    private fun sunriseSunsetCalc(latitude: Double, longitude: Double): Pair<String, String> {
        val loc = com.luckycatlabs.sunrisesunset.dto.Location(latitude, longitude)
        val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Tokyo")
        val calendar: Calendar = Calendar.getInstance(timeZone)

        val calculator = SunriseSunsetCalculator(loc, timeZone)
        val sunriseTime: String = calculator.getOfficialSunriseForDate(calendar)
        val sunsetTime: String = calculator.getOfficialSunsetForDate(calendar)
        println(sunriseTime)
        println(sunsetTime)
        return Pair(sunriseTime, sunsetTime)
    }

    private fun initDb() {
        cityDbController = AppDBController(this)
    }

    private fun updateLoggingButton() {
        val buttonLogging = findViewById<Button>(R.id.button_logging)
        if (buttonLogging != null) {
            if (!LoggerService.isRunning(this)) {
                // ロガーがまだ起動していなければ、ボタンのラベルを「開始」に変更
                buttonLogging.setTextColor(Color.RED)
                buttonLogging.text = getStr(R.string.logging_start)
            } else {
                // 起動済みならば、ボタンのラベルを「停止」に変更
                buttonLogging.setTextColor(Color.WHITE)
                buttonLogging.text = getStr(R.string.logging_stop)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_home -> {
                val WebViewBottom = findViewById<WebView>(R.id.webview_bottom)
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val url = sharedPreferences.getString("pref_webview_bottom_url", "") ?: ""
                if(url!="" && url.startsWith("http")) {
                    WebViewBottom.loadUrl(url)
                }
                return true
            }
            R.id.action_settings -> {
                startActivity(Intent(applicationContext, SettingsActivity::class.java))
                return true
            }
            R.id.action_kml_list -> {
                listKml()
                return true
            }
            R.id.action_kml_export -> {
                exportKml()
                return true
            }
            R.id.action_kml_export_date -> {
                exportKmlDate()
                return true
            }
            R.id.action_kml_clean -> {
                cleanKml()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()

        stopLocationUpdates()
        startLocationUpdates()

        startNetworkCallback()
    }

    override fun onPause() {
        super.onPause()

        stopLocationUpdates()

        stopNetworkCallback()
    }

    private fun requestPermission() {
        val permissionAccessCoarseLocationApproved =
            ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

        if (permissionAccessCoarseLocationApproved) {
            val backgroundLocationPermissionApproved = ActivityCompat
                .checkSelfPermission(this, ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

            if (backgroundLocationPermissionApproved) {
                val externalStoragePermissionApproved = ActivityCompat
                    .checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED

                if (externalStoragePermissionApproved) {
                    // Granted
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(WRITE_EXTERNAL_STORAGE),
                        PERMISSION_REQUEST_CODE
                    )
                }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    ACCESS_COARSE_LOCATION,
                    ACCESS_FINE_LOCATION,
                    ACCESS_BACKGROUND_LOCATION
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = createLocationRequest() ?: return
        if (ActivityCompat.checkSelfPermission(
                this,
                ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
            return
        }
        if (!isLocationUpdatesStarted) {
            if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
                isLocationUpdatesStarted = true
            }
        }
    }

    private fun stopLocationUpdates() {
        if (isLocationUpdatesStarted) {
            if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isLocationUpdatesStarted = false
            }
        }
    }

    private fun createLocationRequest(): LocationRequest? {
        return LocationRequest.create().apply {
            interval = INTERVAL_MILLISECONDS
            fastestInterval = INTERVAL_MILLISECONDS
            smallestDisplacement = INTERVAL_METERS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    // 接続状況
    private fun startNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun stopNetworkCallback() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isDebugMode) {
                Log.v(packageNameString, "onAvailable() $network")
            }
            checkConnection()
        }

        override fun onLost(network: Network) {
            if (isDebugMode) {
                Log.v(packageNameString, "onAvailable() $network")
            }
            checkConnection()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (isDebugMode) {
                Log.v(packageNameString, "onCapabilitiesChanged() $networkCapabilities")
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (isDebugMode) {
                Log.v(packageNameString, "onLinkPropertiesChanged() $linkProperties")
            }
        }

    }

    private fun checkConnection() {
        if (isDebugMode) {
            Log.v(packageNameString, "checkConnection()")
        }

        var wifiAvailable = false
        var cellulerAvailable = false
        connectivityManager.allNetworks.forEach { network ->
            // if (isDebugMode) {
            //      Log.v(
            //         packageNameString,
            //         "checkConnection() network: $network"
            //     )
            // }
            if (connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            ) {
                if (isDebugMode) {
                    Log.v(
                        packageNameString,
                        "checkConnection() network: $network WIFI"
                    )
                }
                wifiAvailable = true
            } else if (connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            ) {
                if (isDebugMode) {
                    Log.v(
                        packageNameString,
                        "checkConnection() network: $network CELLULAR"
                    )
                }
                cellulerAvailable = true
            }
        }

        val c = getStr(R.string.network_state_available)
        val nc = getStr(R.string.network_state_no_networks)

        val textviewNetworkState = findViewById<TextView>(R.id.textview_network_state)
        if (textviewNetworkState != null) {
            textviewNetworkState.text =
                if (wifiAvailable && cellulerAvailable) "wifi/cell $c" else (
                        if (wifiAvailable) "wifi $c" else (
                                if (cellulerAvailable) "cell $c" else "$nc"))
        }
    }

    private fun getStr(resId: Int): String {
        return try {
            getString(resId)
        } catch (e: Exception) {
            ""
        }
    }

    private fun cleanKml() {
        if (isDebugMode) {
            Log.v(packageNameString, "cleanKml()")
        }

        val ioUtil = IoUtil(this)
        val externalPrivateTextFiles = ioUtil.listExternalPrivateTextFiles()

        val stringList = mutableListOf<String>()
        for (f in externalPrivateTextFiles) {
            stringList.add(f.name)
            if (isDebugMode) {
                Log.v(packageNameString, "cleanKml() stringList.add: ${f.name}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getStr(R.string.kml_clean_dialog) + " " + getStr(R.string.kml_clean_message))
            .setItems(stringList.toTypedArray()) { _, which ->
                if (isDebugMode && isDebugModeLoop) {
                    Log.v(
                        packageNameString,
                        "cleanKml() externalPrivateTextFiles[$which]: ${externalPrivateTextFiles[which]}"
                    )
                }
                try {
                    externalPrivateTextFiles[which].delete()

                    Toast.makeText(
                        this,
                        getStr(R.string.action_kml_deleted),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    if (isDebugMode) {
                        Log.e(
                            packageNameString,
                            "cleanKml() $e"
                        )
                    }
                }
            }
            .setPositiveButton(getStr(R.string.clean_all_kmls)) { _, _ ->
                for (f in externalPrivateTextFiles) {
                    if (isDebugMode && isDebugModeLoop) {
                        Log.v(packageNameString, "cleanKml() f:$f")
                    }
                    try {
                        f.delete()
                    } catch (e: Exception) {
                        if (isDebugMode) {
                            Log.e(
                                packageNameString,
                                "cleanKml() $e"
                            )
                        }
                    }
                }

                Toast.makeText(
                    this,
                    getStr(R.string.action_kml_cleaned),
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun listKml() {
        if (isDebugMode) {
            Log.v(packageNameString, "listKml()")
        }

        safContent = ""

        val ioUtil = IoUtil(this)
        val externalPrivateTextFiles = ioUtil.listExternalPrivateTextFiles()
        if (externalPrivateTextFiles.isEmpty()) {
            Toast.makeText(
                this,
                getStr(R.string.action_kml_list_empty),
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val stringList = mutableListOf<String>()
        for (f in externalPrivateTextFiles) {
            stringList.add(f.name)
        }

        AlertDialog.Builder(this)
            .setTitle(getStr(R.string.kml_list_dialog))
            .setItems(stringList.toTypedArray()) { _, which ->
                if (isDebugMode && isDebugModeLoop) {
                    Log.v(
                        packageNameString,
                        "listKml() externalPrivateTextFiles[$which]: ${externalPrivateTextFiles[which]}"
                    )
                }
            }
            .show()
    }

    private fun exportKml(term: String="") {
        if (isDebugMode) {
            Log.v(packageNameString, "exportKml()")
        }

        safContent = ""

        val ioUtil = IoUtil(this)
        val externalPrivateTextFiles = ioUtil.listExternalPrivateTextFiles(term)
        if (externalPrivateTextFiles.isEmpty()) {
            Toast.makeText(
                this,
                getStr(R.string.action_kml_export_empty),
                Toast.LENGTH_LONG
            ).show()

            return
        }

        Toast.makeText(
            this,
            getStr(R.string.action_kml_exporting),
            Toast.LENGTH_LONG
        ).show()

        val sbFileName = StringBuilder()
        val sbFileContent = StringBuilder()

        val sep = System.getProperty("line.separator")

        sbFileContent.append(
            KmlUtil.KmlHeader
        ).append(sep)

        var routeNumber = 0
        for (f in externalPrivateTextFiles) {
            routeNumber++

            if (isDebugMode && isDebugModeLoop) {
                Log.v(packageNameString, "exportKml() f:$f")
            }

            // 各ファイル各行の処理
            var pointsCoordinatesString = ""
            var pathsCoordinatesString = ""
            val rows = ioUtil.readExternalPrivateTextFile(f.name)

            var pointNumber = 0
            for (row in rows) {
                if (isDebugMode && isDebugModeLoop) {
                    Log.v(
                        packageNameString,
                        "exportKml() reverseGeocode row: $row"
                    )
                }

                if (row != "") {
                    if (row.split(",").size == 5) {

                        if (0 == pointNumber) {
                            // 最初の行
                            var fnameToAppend = f.nameWithoutExtension
                            if (row.split(",")[0] != "" && row.split(",")[1] != "") {
                                try {
                                    // 最初の行の経緯度から逆ジオコーディング
                                    val lngDouble = parseDouble(
                                        row.split(",")[0]
                                    ) // 経度が先
                                    val latDouble = parseDouble(row.split(",")[1])

                                    // 測位に成功している場合
                                    if (latDouble >= -90.0 && lngDouble >= -180.0) {
                                        // 逆ジオコーディング
                                        if (::cityDbController.isInitialized) {
                                            val searched =
                                                cityDbController.searchCity(latDouble, lngDouble)

                                            if (isDebugMode && isDebugModeLoop) {
                                                Log.v(
                                                    packageNameString,
                                                    "exportKml() reverseGeocode searched: ${searched?.first} ${searched?.second}"
                                                )
                                            }

                                            if (!searched?.first.isNullOrEmpty()) {
                                                fnameToAppend = searched!!.first
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (isDebugMode) {
                                        Log.e(
                                            packageNameString,
                                            "exportKml() reverseGeocode row: $row $e"
                                        )
                                    }
                                }
                            }
                            // ファイル名のパーツを設定
                            if (isDebugMode && isDebugModeLoop) {
                                Log.v(
                                    packageNameString,
                                    "exportKml() reverseGeocode fnameToAppend: $fnameToAppend"
                                )
                            }
                            sbFileName.append(fnameToAppend).append("_")
                        }

                        pointNumber++

                        val coordinatesString =
                            row.split(",")[0] + "," + // longitude
                                    row.split(",")[1] + "," + // latitude
                                    row.split(",")[2] // altitude
                        val speedString = row.split(",")[3] // speed
                        val datetimeString = row.split(",")[4] // dfIso
                        pointsCoordinatesString += """                    <Placemark>
						<name>${"%02d".format(routeNumber)}${"%04d".format(pointNumber)}S${speedString}</name>
						<TimeStamp>
							<when>${datetimeString}</when>
						</TimeStamp>
						<styleUrl>#route</styleUrl>
						<Point>
							<coordinates>${coordinatesString}</coordinates>
						</Point>
						<ExtendedData>
							<Data name="IconNumber">
								<value>901001</value>
							</Data>
						</ExtendedData>
					</Placemark>
                    """
                        pathsCoordinatesString += coordinatesString + System.getProperty("line.separator")
                    }
                }
            }
            if (isDebugMode && isDebugModeLoop) {
                Log.v(
                    packageNameString,
                    "exportKml() f.name:${f.name} rows:$rows"
                )
            }

            if(rows.isNotEmpty()) {
                sbFileContent.append(
                    """
			<Folder>
				<name>Route${"%02d".format(routeNumber)}</name>
				<Folder>
					<name>Points</name>
					$pointsCoordinatesString
				</Folder>
				<Placemark>
					<name>Path</name>
					<Style>
						<LineStyle>
							<color>ff0000ff</color>
							<width>2</width>
						</LineStyle>
					</Style>
					<LineString>
						<tessellate>1</tessellate>
						<coordinates>
$pathsCoordinatesString
						</coordinates>
					</LineString>
				</Placemark>
			</Folder>
            """
                ).append(sep)
            }
        }

        sbFileContent.append(
            KmlUtil.KmlFooter
        )

        val concatFilename = sbFileName.toString()
        if(concatFilename == "") {
            Toast.makeText(
                this,
                getStr(R.string.action_kml_export_empty),
                Toast.LENGTH_LONG
            ).show()
        } else {
            if (isDebugMode) {
                Log.v(
                    packageNameString,
                    "exportKml() reverseGeocode concatFilename: $concatFilename"
                )
            }

            val date = Date()
            val sdfFyyyyMMddHH = SimpleDateFormat("yyyyMMddHHmmss", Locale.JAPAN)
            val dateString = sdfFyyyyMMddHH.format(date)
            val filenameHeader = "Route${dateString}_"

            saveExternalPublicTextFile(
                filenameHeader + concatFilename.substring(
                    0,
                    concatFilename.length - 1
                ) + ".kml"
            )
            safContent = sbFileContent.toString()
        }
    }

    private fun exportKmlDate() {
        if (isDebugMode) {
            Log.v(packageNameString, "exportKmlDate()")
        }

        val ioUtil = IoUtil(this)
        val externalPrivateTextFiles = ioUtil.listExternalPrivateTextFiles()
        if (externalPrivateTextFiles.isEmpty()) {
            Toast.makeText(
                this,
                getStr(R.string.action_kml_list_empty),
                Toast.LENGTH_LONG
            ).show()

            return
        }

        var stringList = mutableListOf<String>()
        for (f in externalPrivateTextFiles) {
            stringList.add(f.name.substring("Route".length, "RouteyyyyMMdd".length))
        }
        stringList = stringList.toSet().toMutableList()

        AlertDialog.Builder(this)
            .setTitle(getStr(R.string.kml_list_dialog))
            .setItems(stringList.toTypedArray()) { _, which ->
                var term = stringList.toTypedArray()[which]

                if (isDebugMode && isDebugModeLoop) {
                    Log.v(
                        packageNameString,
                        "exportKmlDate() stringList.toTypedArray()[$which]: ${term}"
                    )
                }

                try {
                    exportKml("Route$term")
                } catch (e: Exception) {
                    if (isDebugMode) {
                        Log.e(
                            packageNameString,
                            "exportKmlDate() $e"
                        )
                    }
                }
            }
            .show()
    }

    // Storage Access Framework
    private fun saveExternalPublicTextFile(filename: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        // intent.type = "text/plain"
        intent.type = "application/vnd.google-earth.kml+xml"
        intent.putExtra(Intent.EXTRA_TITLE, filename)
        startActivityForResult(intent, WRITE_REQUEST_CODE)
    }

    private var safContent = ""
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (isDebugMode) {
            Log.v(
                packageNameString,
                "onActivityResult() data:$data"
            )
            Log.v(packageNameString, "onActivityResult() safContent:$safContent")
        }

        if (requestCode == WRITE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    outputStream?.write(safContent.toByteArray())
                }
            }
        }
    }

    class NotificationClickedBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (instance != null && instance is MainActivity) {
                val buttonLocate = instance!!.findViewById<Button>(R.id.button_tweet_location)
                buttonLocate.performClick()
            }
        }
    }

    class NotificationRemoteBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (instance != null && instance is MainActivity) {
                val remoteText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(LoggerService.INPUT_TWEET)
                if (remoteText != null && remoteText.isNotEmpty()) {
                    val tweetMain = instance!!.findViewById<EditText>(R.id.tweet_main)
                    tweetMain.setText(remoteText)
                    val iconImage = instance!!.findViewById<ImageButton>(R.id.icon_image)
                    iconImage.performClick()
                }
            }
        }
    }


}