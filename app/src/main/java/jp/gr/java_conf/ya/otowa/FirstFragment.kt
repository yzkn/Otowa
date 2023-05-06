// Copyright (c) 2023 YA-androidapp(https://github.com/YA-androidapp) All rights reserved.
package jp.gr.java_conf.ya.otowa

import android.annotation.SuppressLint
import android.content.Context.AUDIO_SERVICE
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.BatteryManager
import android.os.BatteryManager.*
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import java.lang.Double.parseDouble


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private lateinit var audioManager: AudioManager
    private lateinit var cityDbController: AppDBController
    private lateinit var createdView: View
    private lateinit var sensorManager: SensorManager
    private lateinit var soundPool: SoundPool

    private var isDebugMode = false
    private var loadedSoundDeleted = -1
    private var loadedSoundNotify = -1
    private var loadedSoundTweeted = -1
    private var packageNameString = ""
    private var prefLocatingError = 0.0
    private var preTweetedText = ""

    // 初期化処理
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = context?.getSystemService(AUDIO_SERVICE) as AudioManager
        sensorManager = context?.getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        packageNameString = activity?.packageName.toString()
        createdView = view
        initialize()

        initDb()
    }

    override fun onResume() {
        super.onResume()

        requireView().isFocusableInTouchMode = true
        requireView().requestFocus()
        requireView().setOnKeyListener { _: View?, keyCode: Int, event: KeyEvent? ->

            if (event?.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                reverseGeocode(true)

                playSound(loadedSoundNotify)
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        soundPool.release()

        super.onDestroy()
    }

    private fun initDb() {
        if (::createdView.isInitialized) {
            cityDbController = AppDBController(createdView.context)
        }
    }

    private fun initSound() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(1)
            .build()
        soundPool.setOnLoadCompleteListener { soundPool, sampleId, status ->
            if (isDebugMode) {
                Log.v(
                    packageNameString,
                    "initSound() soundPool.setOnLoadCompleteListener soundPool: $soundPool sampleId: $sampleId status: $status"
                )
            }
        }

        // 音楽の読み込み
        loadedSoundDeleted = soundPool.load(context, R.raw.deleted, 1)
        loadedSoundNotify = soundPool.load(context, R.raw.notify, 1)
        loadedSoundTweeted = soundPool.load(context, R.raw.tweeted, 1)
    }

    private fun playSound(soundId: Int) {
        if (soundId != -1) {
            soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f)
        }
    }

    private fun initialize() {
        if (::createdView.isInitialized) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)

            initSound()

            val prefLocatingErrorString =
                sharedPreferences.getString("pref_locating_error", "0") ?: "0"
            if (prefLocatingErrorString != "") {
                try {
                    prefLocatingError = parseDouble(prefLocatingErrorString)
                } catch (e: Exception) {
                    if (isDebugMode) {
                        Log.e(
                            packageNameString,
                            "initialize() prefLocatingErrorString: $prefLocatingErrorString $e"
                        )
                    }
                }
            }

            val accessKey = sharedPreferences.getString("pref_auth_access_key", "") ?: ""
            val accessSecret = sharedPreferences.getString("pref_auth_access_secret", "") ?: ""
            if (isDebugMode) {
                Log.v(packageNameString, "initialize() AK: $accessKey")
                Log.v(packageNameString, "initialize() AS: $accessSecret")
            }

            // UIパーツの取得
            val iconImage = createdView.findViewById<ImageButton>(R.id.icon_image)
            val buttonClear = createdView.findViewById<Button>(R.id.button_clear)
            val buttonLocate = createdView.findViewById<Button>(R.id.button_locate)

            val buttonTweetLocation = createdView.findViewById<Button>(R.id.button_tweet_location)

            // EditText群に対して設定
            val editTextList = arrayOf(
                createdView.findViewById<EditText>(R.id.tweet_prefix),
                createdView.findViewById<EditText>(R.id.tweet_main),
                createdView.findViewById<EditText>(R.id.tweet_suffix)
            )
            for (editTextItem in editTextList) {
                editTextItem.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable) {
                        val buf = StringBuilder().also {
                            for (editTextItem2 in editTextList) {
                                it.append(editTextItem2.text)
                            }
                        }
                        val textLen = buf.toString().length
                        buttonClear.isEnabled = textLen > 0
                    }
                })
            }

            // ボタンのクリックイベントを設定
            buttonClear.setOnClickListener {
                editTextList[1].text.clear()
            }
            buttonClear.setOnLongClickListener {
                for (editTextItem in editTextList) {
                    editTextItem.text.clear()
                }
                true
            }
            buttonLocate.setOnClickListener {
                reverseGeocode()

                playSound(loadedSoundNotify)
            }
            iconImage.setOnClickListener {
                createdView.findViewById<ImageButton>(R.id.icon_image).isEnabled = false

                val buf = StringBuilder().also {
                    for (editTextItem2 in editTextList) {
                        it.append(editTextItem2.text)
                    }
                }
                val tweettext = buf.toString()
                updateTweet(tweettext)

                createdView.findViewById<ImageButton>(R.id.icon_image).isEnabled = true
                editTextList[1].text.clear()

                playSound(loadedSoundTweeted)
            }

            buttonTweetLocation.setOnClickListener {
                reverseGeocode(true)

                playSound(loadedSoundNotify)
            }

            changeScreenBrightness()
            initializeSensors()
            initializeLoggingButton()
        }
    }

    private fun initializeLoggingButton() {
        val buttonLogging = createdView.findViewById<Button>(R.id.button_logging)
        if (buttonLogging != null) {
            if (!LoggerService.isRunning(requireContext())) {
                // ロガーがまだ起動していなければ、ボタンのラベルを「開始」に変更
                buttonLogging.setTextColor(Color.RED)
                buttonLogging.text = getStr(R.string.logging_start)
            } else {
                // 起動済みならば、ボタンのラベルを「停止」に変更
                buttonLogging.setTextColor(Color.WHITE)
                buttonLogging.text = getStr(R.string.logging_stop)
            }
            buttonLogging.setOnClickListener {
                Toast.makeText(
                    activity,
                    getStr(R.string.have_to_long_press_logging_button),
                    Toast.LENGTH_SHORT
                ).show()
            }
            buttonLogging.setOnLongClickListener {
                buttonLogging.isEnabled = false

                val loggerServiceIntent = Intent(requireContext(), LoggerService::class.java)
                if (!LoggerService.isRunning(requireContext())) {
                    // ロガーがまだ起動していなければ、ロガーを起動
                    activity?.startForegroundService(loggerServiceIntent)

                    // ボタンのラベルを「停止」に変更
                    buttonLogging.setTextColor(Color.WHITE)
                    buttonLogging.text = getStr(R.string.logging_stop)
                } else {
                    // 起動済みならば、ロガーを停止
                    activity?.stopService(loggerServiceIntent)

                    // ボタンのラベルを「開始」に変更
                    buttonLogging.setTextColor(Color.RED)
                    buttonLogging.text = getStr(R.string.logging_start)
                }

                buttonLogging.isEnabled = true

                // return
                true
            }
        }
    }

    private fun initializeSensors() {
        if (::sensorManager.isInitialized) {
            val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
            if (isDebugMode) {
                Log.v(
                    packageNameString,
                    "initializeSensors() sensors:"
                )
            }
            if (sensorList.size > 0) {
                for (s in sensorList) {
                    if (isDebugMode) {
                        Log.v(packageNameString, "initializeSensors() sensors: s: $s")
                    }
                    sensorManager.registerListener(
                        object : SensorEventListener {
                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                            }

                            override fun onSensorChanged(event: SensorEvent) {
                                // if (isDebugMode) {
                                //     Log.v(
                                //         packageNameString,
                                //         "initializeSensors() onSensorChanged() sensor: ${event.sensor}"
                                //     )
                                // }

                                if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                                    val resultValues: FloatArray = event.values.clone()
                                    if (::createdView.isInitialized) {
                                        val textView =
                                            createdView.findViewById(R.id.textview_pressure) as TextView
                                        textView.text =
                                            "%04.0f".format(resultValues[0]) + getStr(R.string.pressure_hpa)
                                    }
                                } else if (event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                                    val resultValues: FloatArray = event.values.clone()
                                    if (::createdView.isInitialized) {
                                        val textView =
                                            createdView.findViewById(R.id.textview_ambient_temperature) as TextView
                                        textView.text =
                                            "%02.1f".format(resultValues[0]) + getStr(R.string.temp_c)
                                    }
                                }
                            }
                        }, s, SensorManager.SENSOR_DELAY_NORMAL
                    )
                }
            }
        }
    }

    private fun changeScreenBrightness() {
        if (isDebugMode) {
            Log.v(packageNameString, "changeScreenBrightness()")
        }

        val batteryInfo: Intent = activity?.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return
        val plugged = batteryInfo.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        if (isDebugMode) {
            Log.v(packageNameString, "changeScreenBrightness() plugged: $plugged")
        }

        when (plugged) {
            BATTERY_PLUGGED_AC, BATTERY_PLUGGED_USB, BATTERY_PLUGGED_WIRELESS -> {
                val lp: WindowManager.LayoutParams? = activity?.getWindow()?.getAttributes()
                if (lp != null) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    activity?.getWindow()?.setAttributes(lp)
                    if (isDebugMode) {
                        Log.v(
                            packageNameString,
                            "changeScreenBrightness() BRIGHTNESS_OVERRIDE_FULL"
                        )
                    }
                }
            }
        }
    }

    private fun updateTweet(tweetText: String) {
        if (tweetText != "") {
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, tweetText)
                .let { Intent.createChooser(it, null) }
                .also(::startActivity)
        }
    }

    private fun getLocationAddedError(latString: String, lngString: String): Pair<Double, Double> {
        if (isDebugMode) {
            Log.v(
                packageNameString,
                "getLocationAddedError() latString: $latString lngString: $lngString"
            )
        }

        val latError =
            prefLocatingError * (Math.random() - 0.5) // Math.random() が +0 - +1 の範囲なので () 内は -0.5 - +0.5 の範囲
        val lngError = prefLocatingError * (Math.random() - 0.5)

        // 誤差を追加
        var currentLatitude = parseDouble(latString) + latError
        var currentLongitude = parseDouble(lngString) + lngError

        // 範囲内に収める
        if (currentLatitude > 90.0) {
            currentLatitude = 180.0 - currentLatitude // 91 => 89
        } else if (currentLatitude < -90) {
            currentLatitude = -1.0 * (currentLatitude + 180.0) // -91 => -89
        }
        if (currentLongitude > 180.0) {
            currentLongitude -= 360 // 181 => -179
        } else if (currentLongitude < -180.0) {
            currentLongitude += 360  // -181 => 179
        }

        if (isDebugMode) {
            Log.v(
                packageNameString,
                "getLocationAddedError() $latString $currentLatitude , $lngString $currentLongitude"
            )
        }

        return currentLatitude to currentLongitude
    }

    @SuppressLint("SetTextI18n")
    public fun reverseGeocode(direct: Boolean = false) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val latString = sharedPreferences.getString("pref_current_latitude", "") ?: ""
        val lngString = sharedPreferences.getString("pref_current_longitude", "") ?: ""

        if (latString != "" && lngString != "") {
            try {
                var currentLatLng = getLocationAddedError(latString, lngString)

                // 測位に成功している場合
                if (currentLatLng.first >= -90.0 && currentLatLng.second >= -180.0) {
                    // 逆ジオコーディング
                    if (::cityDbController.isInitialized) {
                        val searched =
                            cityDbController.searchCity(currentLatLng.first, currentLatLng.second)
                        val url =
                            "https://www.google.com/maps/search/?api=1&query=${currentLatLng.first},${currentLatLng.second}"

                        if (isDebugMode) {
                            Log.v(
                                packageNameString,
                                "reverseGeocode() searched: ${searched?.first} ${searched?.second}"
                            )
                            Log.v(
                                packageNameString,
                                "reverseGeocode() searched: url: $url"
                            )
                        }

                        if (!searched?.first.isNullOrEmpty() && !searched?.second.isNullOrEmpty()) {
                            if (direct) {
                                val prefixText =
                                    createdView.findViewById<EditText>(R.id.tweet_prefix).text.toString()
                                val suffixText =
                                    createdView.findViewById<EditText>(R.id.tweet_suffix).text.toString()
                                val buf = StringBuilder().also {
                                    it.append(
                                        prefixText + (if (prefixText.isNotEmpty() && (" " != prefixText.substring(
                                                prefixText.length - 1,
                                                1
                                            ))
                                        ) " " else "")
                                    )
                                    it.append(searched?.first)
                                    it.append(searched?.second)
                                    it.append(" ")
                                    it.append(url)
                                    it.append(
                                        (if (suffixText.isNotEmpty() && (" " != suffixText.substring(
                                                0,
                                                1
                                            ))
                                        ) " " else "") + suffixText
                                    )
                                }
                                val tweetText = buf.toString()
                                if (
                                    (tweetText.contains(" ")) &&
                                    (preTweetedText.contains(" ")) &&
                                    (tweetText.split(" ")[0] == preTweetedText.split(" ")[0])
                                ) {
                                    Toast.makeText(
                                        activity,
                                        getStr(R.string.duplicated_tweet_text),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    updateTweet(tweetText)
                                    playSound(loadedSoundNotify)
                                }
                                preTweetedText = tweetText
                            } else {
                                val mainEditText = view?.findViewById<EditText>(R.id.tweet_main)
                                if (mainEditText != null) {
                                    val tweetMainText = mainEditText.text.toString()
                                    val buf = StringBuilder().also {
                                        it.append(searched?.first)
                                        it.append(searched?.second)
                                        it.append(" ")
                                        it.append(url)
                                    }
                                    mainEditText.setText(
                                        (if (tweetMainText.isNotEmpty()) "$tweetMainText " else "") + buf.toString()
                                    )
                                    mainEditText.requestFocus()
                                    mainEditText.setSelection(mainEditText.text.length)
                                    playSound(loadedSoundNotify)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.v(
                        packageNameString,
                        "reverseGeocode() latString: $latString lngString: $lngString $e"
                    )
                }
            }
        }
    }

    private fun getStr(resId: Int): String {
        return try {
            getString(resId)
        } catch (e: Exception) {
            ""
        }
    }
}
