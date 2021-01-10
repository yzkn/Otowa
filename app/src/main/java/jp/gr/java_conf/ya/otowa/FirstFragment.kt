package jp.gr.java_conf.ya.otowa

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.BatteryManager.*
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder
import java.lang.Double.parseDouble
import java.lang.Exception


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    // 定数
    val callbackUrl = "callback://"

    // 変数
    var isDebugMode = false
    var packageNameString = ""

    lateinit var audioManager: AudioManager

    lateinit var oauthTwitter: Twitter
    lateinit var apiTwitter: Twitter
    lateinit var createdView: View
    lateinit var cityDbController: AppDBController

    // 初期化処理
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        audioManager = context?.getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (isDebugMode) {
            Log.v(packageNameString, "onOptionsItemSelected() item: $item")
        }
        when (item.itemId) {
            R.id.action_twitter_profile_image -> {
                if (isDebugMode) {
                    Log.v(packageNameString, "action_twitter_profile_image")
                }
                reloadProfileImage()
                return false
            }
        }
        return true
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

    private fun initDb() {
        if (::createdView.isInitialized) {
            cityDbController = AppDBController(createdView.context)
        }
    }

    private fun initialize() {
        if (::createdView.isInitialized) {

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)

            val accessKey = sharedPreferences.getString("pref_auth_access_key", "") ?: ""
            val accessSecret = sharedPreferences.getString("pref_auth_access_secret", "") ?: ""
            if (isDebugMode) {
                Log.v(packageNameString, "initialize() AK: $accessKey")
                Log.v(packageNameString, "initialize() AS: $accessSecret")
            }

            // UIパーツの取得
            val linearLayoutFirst = createdView.findViewById<LinearLayout>(R.id.linear_layout_first)
            val linearLayoutTweet = createdView.findViewById<LinearLayout>(R.id.linear_layout_tweet)

            // 認証済みか否かで、UIの可視状態を切り替え
            if (accessKey.length > 40 && accessSecret.length > 40) {
                linearLayoutFirst.visibility = View.GONE;
                linearLayoutTweet.visibility = View.VISIBLE;

                val iconImage = createdView.findViewById<ImageView>(R.id.icon_image)
                val buttonClear = createdView.findViewById<Button>(R.id.button_clear)
                val buttonLocate = createdView.findViewById<Button>(R.id.button_locate)
                val buttonTweet = createdView.findViewById<Button>(R.id.button_tweet)

                val buttonVolumeDec = createdView.findViewById<Button>(R.id.button_volume_dec)
                val buttonVolumeInc = createdView.findViewById<Button>(R.id.button_volume_inc)
                val buttonVolumeMax = createdView.findViewById<Button>(R.id.button_volume_max)

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
                            buttonTweet.text = textLen.toString()
                            buttonClear.isEnabled = textLen > 0
                            buttonTweet.isEnabled = textLen > 0
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
                }
                buttonTweet.setOnClickListener {
                    createdView.findViewById<Button>(R.id.button_tweet).isEnabled = false

                    val buf = StringBuilder().also {
                        for (editTextItem2 in editTextList) {
                            it.append(editTextItem2.text)
                        }
                    }
                    val tweettext = buf.toString()
                    updateTweet(tweettext)

                    createdView.findViewById<Button>(R.id.button_tweet).isEnabled = true
                    editTextList[1].text.clear()
                }

                buttonVolumeDec.setOnClickListener {
                    volumeDecrease()
                }
                buttonVolumeInc.setOnClickListener {
                    volumeIncrease()
                }
                buttonVolumeMax.setOnClickListener {
                    volumeMax()
                }

                checkVolume()
                changeScreenBrightness()

                iconImage.setOnLongClickListener {
                    var stringList: MutableList<String>
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                        val tweetList = withContext(Dispatchers.IO) { getTw().userTimeline }
                        stringList = mutableListOf<String>()
                        for (tweet in tweetList) {
                            stringList.add(tweet.text)
                        }

                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(activity)
                                .setTitle(getString(R.string.tweet_delete_dialog))
                                .setItems(stringList.toTypedArray()) { _, which ->
                                    AlertDialog.Builder(activity)
                                        .setTitle(getString(R.string.tweet_delete_dialog))
                                        .setMessage(stringList[which])
                                        .setPositiveButton(getString(R.string.delete_tweet)) { _, _ ->
                                            deleteTweet(tweetList[which].id)
                                        }
                                        .show()
                                }
                                .show()
                        }
                    }
                    true
                }

                val profileImageUrlHttps =
                    sharedPreferences.getString("pref_profile_image_url_https", "")
                        ?: ""
                if (isDebugMode) {
                    Log.v(
                        packageNameString,
                        "initialize() profileImageUrlHttps: $profileImageUrlHttps"
                    )
                }

                if (profileImageUrlHttps != "" && profileImageUrlHttps.startsWith("http")) {
                    Picasso.get()
                        .load(profileImageUrlHttps)
                        .into(iconImage);
                }
            } else {
                val buttonFirst = createdView.findViewById<Button>(R.id.button_first)
                buttonFirst.setOnClickListener {
                    getRequestToken()
                }
                linearLayoutFirst.visibility = View.VISIBLE;
                linearLayoutTweet.visibility = View.GONE;
            }
        }
    }

    private fun checkVolume() {
        if (::audioManager.isInitialized) {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (isDebugMode) {
                Log.v(
                    packageNameString,
                    "checkVolume() currentVolume: $currentVolume minVolume: $minVolume maxVolume: $maxVolume"
                )
            }

            val buttonVolumeDec = createdView.findViewById<Button>(R.id.button_volume_dec)
            val buttonVolumeInc = createdView.findViewById<Button>(R.id.button_volume_inc)
            val buttonVolumeMax = createdView.findViewById<Button>(R.id.button_volume_max)
            when (currentVolume) {
                minVolume -> {
                    buttonVolumeDec.isEnabled = false
                    buttonVolumeInc.isEnabled = true
                    buttonVolumeMax.isEnabled = true
                }
                maxVolume -> {
                    buttonVolumeDec.isEnabled = true
                    buttonVolumeInc.isEnabled = false
                    buttonVolumeMax.isEnabled = false
                }
                else -> {
                    buttonVolumeDec.isEnabled = true
                    buttonVolumeInc.isEnabled = true
                    buttonVolumeMax.isEnabled = true
                }
            }
        }
    }

    private fun volumeDecrease() {
        if (::audioManager.isInitialized) {
            val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVolume - 1 >= minVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume - 1, 0)
                checkVolume()
            }
        }
    }

    private fun volumeIncrease() {
        if (::audioManager.isInitialized) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVolume + 1 <= maxVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume + 1, 0)
                checkVolume()
            }
        }
    }

    private fun volumeMax() {
        if (::audioManager.isInitialized) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            checkVolume()
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

    // Twitter認証処理

    private fun getMetadata(key: String): String {
        if (activity != null) {
            return activity?.getPackageManager()?.getApplicationInfo(
                packageNameString,
                PackageManager.GET_META_DATA
            )?.metaData?.getString(key)
                ?: ""
        }

        return ""
    }

    private fun getCkCs(): Pair<String, String> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val consumerKey = sharedPreferences.getString("pref_auth_consumer_key", "") ?: ""
        val consumerSecret = sharedPreferences.getString("pref_auth_consumer_secret", "") ?: ""
        if (isDebugMode) {
            Log.v(packageNameString, "getCkCs() sharedPreferences CK: $consumerKey")
            Log.v(packageNameString, "getCkCs() sharedPreferences CS: $consumerSecret")
        }
        if (consumerKey.length > 15 && consumerSecret.length > 40) {
            return Pair(consumerKey, consumerSecret)
        }

        if (isDebugMode) {
            Log.v(packageNameString, "getCkCs() env CK: " + getMetadata("OTOWA_CONSUMER_KEY"))
            Log.v(packageNameString, "getCkCs() env CS: " + getMetadata("OTOWA_CONSUMER_SECRET"))
        }
        return Pair(getMetadata("OTOWA_CONSUMER_KEY"), getMetadata("OTOWA_CONSUMER_SECRET"))
    }

    private fun getRequestToken() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val consumerKey = getCkCs().first
            val consumerSecret = getCkCs().second
            if (isDebugMode) {
                Log.v(packageNameString, "getRequestToken() CK: $consumerKey")
                Log.v(packageNameString, "getRequestToken() CS: $consumerSecret")
            }

            val builder = ConfigurationBuilder()
                .setDebugEnabled(true)
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
            val config = builder.build()
            val factory = TwitterFactory(config)
            oauthTwitter = factory.instance

            if (::oauthTwitter.isInitialized) {
                try {
                    val requestToken = oauthTwitter.oAuthRequestToken
                    withContext(Dispatchers.Main) {
                        genTwitterWebviewDialog(requestToken.authorizationURL)
                    }
                } catch (e: IllegalStateException) {
                    if (isDebugMode) {
                        Log.e(packageNameString, "getRequestToken() ERROR: $e")
                    }
                }
            }
        }
    }

    lateinit var twitterDialog: Dialog

    private suspend fun genTwitterWebviewDialog(url: String) {
        if (isDebugMode) {
            Log.v(packageNameString, "genTwitterWebviewDialog() url: $url")
        }

        twitterDialog = Dialog(requireContext())
        val webView = WebView(requireContext())

        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = TwitterWebViewClient()
        webView.loadUrl(url)

        twitterDialog.setContentView(webView)
        twitterDialog.show()
    }

    inner class TwitterWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            if (request?.url.toString().startsWith(callbackUrl)) {
                if (isDebugMode) {
                    Log.v(
                        packageNameString,
                        "shouldOverrideUrlLoading() request.url: " + request?.url.toString()
                    )
                }
                getAccessToken(request?.url.toString())

                if (request?.url.toString().contains(callbackUrl)) {
                    twitterDialog.dismiss()
                }
                return true
            }
            return false
        }
    }

    private fun getAccessToken(url: String) {
        if (isDebugMode) {
            Log.v(packageNameString, "getAccessToken() url: $url")
        }

        if (::oauthTwitter.isInitialized) {
            val uri = Uri.parse(url)
            val oauthVerifier = uri.getQueryParameter("oauth_verifier") ?: ""
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                val accToken = withContext(Dispatchers.IO) {
                    oauthTwitter.getOAuthAccessToken(oauthVerifier)
                }
                if (isDebugMode) {
                    Log.v(packageNameString, "getAccessToken() AK: ${accToken.token}")
                    Log.v(packageNameString, "getAccessToken() AK: ${accToken.tokenSecret}")
                }

                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
                with(sharedPreferences.edit()) {
                    putString("pref_auth_access_key", accToken.token)
                    putString("pref_auth_access_secret", accToken.tokenSecret)
                    putString(
                        "pref_profile_image_url_https",
                        oauthTwitter.verifyCredentials().profileImageURLHttps
                    )

                    commit()
                }

                showUserInfo()
            }
        }
    }

    private suspend fun showUserInfo() {
        if (::oauthTwitter.isInitialized) {
            val usr = withContext(Dispatchers.IO) { oauthTwitter.verifyCredentials() }
            if (usr != null) {
                if (isDebugMode) {
                    Log.v(packageNameString, "showUserInfo() usr.name: ${usr.name}")
                    Log.v(packageNameString, "showUserInfo() usr.screenName: ${usr.screenName}")
                    Log.v(
                        packageNameString,
                        "showUserInfo() usr.profileImageURLHttps: ${usr.profileImageURLHttps}"
                    )
                }

                // 画面再読み込み
                withContext(Dispatchers.Main) {
                    initialize()
                }
            }
        }
    }

    private fun getTw(): Twitter {
        if (::apiTwitter.isInitialized) {
            return apiTwitter
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val builder = ConfigurationBuilder()
        builder.setDebugEnabled(isDebugMode)
            .setOAuthConsumerKey(getCkCs().first)
            .setOAuthConsumerSecret(getCkCs().second)
            .setOAuthAccessToken(
                sharedPreferences.getString("pref_auth_access_key", "")
                    ?: ""
            )
            .setOAuthAccessTokenSecret(
                sharedPreferences.getString("pref_auth_access_secret", "")
                    ?: ""
            )
        val config = builder.build()
        val factory = TwitterFactory(config)

        // キャッシュ
        apiTwitter = factory.getInstance()

        return apiTwitter
    }

    private fun reloadProfileImage() {
        if (isDebugMode) {
            Log.v(packageNameString, "reloadProfileImage()")
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val usr = withContext(Dispatchers.IO) { getTw().verifyCredentials() }
            if (isDebugMode) {
                Log.v(packageNameString, "reloadProfileImage() usr: ${usr.toString()}")
            }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            with(sharedPreferences.edit()) {
                putString(
                    "pref_profile_image_url_https",
                    usr.profileImageURLHttps
                )

                commit()
            }
            if (isDebugMode) {
                Log.v(packageNameString, "reloadProfileImage() uri: ${usr.profileImageURLHttps}")
            }

            withContext(Dispatchers.Main) {
                if (usr.profileImageURLHttps != "" && usr.profileImageURLHttps.startsWith("http")) {
                    val iconImage = activity?.findViewById<ImageView>(R.id.icon_image)
                    Picasso.get()
                        .load(usr.profileImageURLHttps)
                        .into(iconImage);
                }

                Toast.makeText(
                    activity,
                    getString(R.string.reloaded),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateTweet(tweettext: String) {
        if (tweettext != "") {
            try {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    async(Dispatchers.IO) {
                        getTw().updateStatus(tweettext)
                    }.await()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            getString(R.string.tweeted) + tweettext,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: TwitterException) {
                Toast.makeText(
                    activity,
                    e.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun deleteTweet(statusId: Long) {
        if (statusId > -1) {
            try {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    async(Dispatchers.IO) {
                        getTw().destroyStatus(statusId)
                    }.await()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            getString(R.string.deleted),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: TwitterException) {
                Toast.makeText(
                    activity,
                    e.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun reverseGeocode() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val latString = sharedPreferences.getString("pref_current_latitude", "") ?: ""
        val lonString = sharedPreferences.getString("pref_current_longitude", "") ?: ""

        if (latString != "" && lonString != "") {
            try {
                var currentLatitude = parseDouble(latString)
                var currentLongitude = parseDouble(lonString)

                // 測位に成功している場合
                if (currentLatitude >= -90.0 && currentLongitude >= -180.0) {
                    // 逆ジオコーディング
                    if (::cityDbController.isInitialized) {
                        val cityName =
                            cityDbController.searchCity(currentLatitude, currentLongitude)
                        Log.v(packageNameString, "reverseGeocode() City: $cityName")

                        if (!cityName.isNullOrEmpty()) {
                            view?.findViewById<EditText>(R.id.tweet_main)?.setText(
                                view?.findViewById<EditText>(R.id.tweet_main)?.text.toString() + cityName
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.v(packageNameString, "reverseGeocode() latString: $latString $e")
                    Log.v(packageNameString, "reverseGeocode() lonString: $lonString $e")
                }
            }
        }
    }
}
