package jp.gr.java_conf.ya.otowa

import android.Manifest.permission.*
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.*
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import java.util.*


class MainActivity : AppCompatActivity() {

    // 変数
    var isDebugMode = false
    var packageNameString = ""

    var isLocationUpdatesStarted = false

    var updatedCount = 0

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    lateinit var cityDbController: AppDBController

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var connectivityManager: ConnectivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // 設定
        packageNameString = packageName.toString()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)
        with(sharedPreferences.edit()) {
            putString("pref_current_latitude", "")
            putString("pref_current_longitude", "")
            commit()
        }

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
    }

    private fun locate(locationResult: LocationResult) {
        for (location in locationResult.locations) {
            updatedCount++
            if (isDebugMode) {
                Log.v(
                    packageNameString,
                    "onCreate() GPS $updatedCount ${location.latitude} , ${location.longitude}"
                )
            }

            with(
                PreferenceManager.getDefaultSharedPreferences(application).edit()
            ) {
                putString("pref_current_latitude", location.latitude.toString())
                putString("pref_current_longitude", location.longitude.toString())

                commit()
            }

            // 測位ごとに毎回行う
            updateLocateButton(location)
            updateSpeedMeter(location.speed)

            // たまに行う
            if (0 == (updatedCount % 10)) {
                updateSunriseSunsetLabel(location)
            }
        }
    }

    private fun updateLocateButton(location: android.location.Location) {
        // 測位に成功したらボタンのテキストを更新する
        val buttonLocate = findViewById<Button>(R.id.button_locate)
        if (buttonLocate != null) {
            buttonLocate.isEnabled = true
            buttonLocate.textSize = 14F


            // 逆ジオコーディング
            if (::cityDbController.isInitialized) {
                val cityName =
                    cityDbController.searchCity(location.latitude, location.longitude)
                Log.v(packageNameString, "reverseGeocode() City: $cityName")

                if (!cityName.isNullOrEmpty()) {
                    buttonLocate.text = getString(R.string.locate) + " " + cityName
                } else {
                    buttonLocate.text = getString(R.string.locate)
                }
            } else {
                buttonLocate.text = getString(R.string.locate)
            }
        }
    }

    private fun updateSunriseSunsetLabel(location: android.location.Location) {
        val ssPair = sunriseSunsetCalc(location)
        Log.v(packageNameString, "updateSunriseSunsetLabel() ssPair: ${ssPair.first}, ${ssPair.second}")

        // TODO
    }

    private fun updateSpeedMeter(speed: Float) {
        Log.v(packageNameString, "updateSpeedMeter() speed: $speed")

        // TODO
    }

    private fun sunriseSunsetCalc(location: android.location.Location): Pair<String, String> {
        val loc = com.luckycatlabs.sunrisesunset.dto.Location(location.latitude, location.longitude)
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
            R.id.action_settings -> {
                startActivity(Intent(applicationContext, SettingsActivity::class.java))
                return true;
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()

        val batteryInfo: Intent = registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return

        stopLocationUpdates()
        when (batteryInfo.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> startLocationUpdates(
                true
            )
            else -> startLocationUpdates(false)
        }

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
                // Granted
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

    private fun startLocationUpdates(acc: Boolean) {
        val locationRequest = createLocationRequest(acc) ?: return
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

    private fun createLocationRequest(acc: Boolean): LocationRequest? {
        if (acc) {
            Toast.makeText(
                this,
                getString(R.string.location_request_highacc),
                Toast.LENGTH_LONG
            ).show()
            return LocationRequest.create()?.apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
        } else {
            Toast.makeText(
                this,
                getString(R.string.location_request_lowacc),
                Toast.LENGTH_LONG
            ).show()
            return LocationRequest.create()?.apply {
                interval = 60000
                fastestInterval = 60000
                priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            }
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

        val activeNetworks = connectivityManager.allNetworks.mapNotNull {
            connectivityManager.getNetworkCapabilities(it)
        }.filter {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        val isConnected = activeNetworks.isNotEmpty()
        // activeNetworks.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }


        if (isDebugMode) {
            connectivityManager.allNetworks.forEach { network ->
                Log.v(
                    packageNameString,
                    "checkConnection() network: $network"
                )
                if (connectivityManager.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                ) {
                    Log.v(
                        packageNameString,
                        "checkConnection() network: $network WIFI"
                    )
                } else if (connectivityManager.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                ) {
                    Log.v(
                        packageNameString,
                        "checkConnection() network: $network CELLULAR"
                    )
                }
            }
        }

        Toast.makeText(
            this,
            if (isConnected) getString(R.string.network_state_connected) else getString(R.string.network_state_nonetwork),
            Toast.LENGTH_LONG
        ).show()
    }
}