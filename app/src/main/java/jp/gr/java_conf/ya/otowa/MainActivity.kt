package jp.gr.java_conf.ya.otowa

import android.Manifest.permission.*
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.*

class MainActivity : AppCompatActivity() {

    // 変数
    var isDebugMode = false
    var packageNameString = ""

    var isLocationUpdatesStarted = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

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

        // 測位
        var updatedCount = 0
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    updatedCount++
                    if (isDebugMode) {
                        Log.v(
                            packageNameString,
                            "onCreate() GPS ${updatedCount.toString()} ${location.latitude} , ${location.longitude}"
                        )
                    }

                    with(
                        PreferenceManager.getDefaultSharedPreferences(application).edit()
                    ) {
                        putString("pref_current_latitude", location.latitude.toString())
                        putString("pref_current_longitude", location.longitude.toString())

                        commit()
                    }

                    // 測位に成功したらボタンのテキストを更新する
                    val buttonLocate = findViewById<Button>(R.id.button_locate)
                    if (buttonLocate != null) {
                        buttonLocate.isEnabled = true
                        buttonLocate.textSize = 14F
                        buttonLocate.text = getString(R.string.locate)
                    }
                }
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
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
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
}