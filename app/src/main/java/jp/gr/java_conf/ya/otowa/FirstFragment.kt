package jp.gr.java_conf.ya.otowa

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import com.google.android.gms.location.LocationResult
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

    lateinit var oauthTwitter: Twitter
    lateinit var apiTwitter: Twitter
    lateinit var createdView: View
    lateinit var cityDbController: AppDBController

    // 初期化処理
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_twitter_profile_image -> {
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
        cityDbController = AppDBController(createdView.context)
    }

    private fun initialize() {
        val view = createdView

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)

        val accessKey = sharedPreferences.getString("pref_auth_access_key", "") ?: ""
        val accessSecret = sharedPreferences.getString("pref_auth_access_secret", "") ?: ""
        if (isDebugMode) {
            Log.v(packageNameString, "AK: $accessKey")
            Log.v(packageNameString, "AS: $accessSecret")
        }

        // UIパーツの取得
        val linearLayoutFirst = view.findViewById<LinearLayout>(R.id.linear_layout_first)
        val linearLayoutTweet = view.findViewById<LinearLayout>(R.id.linear_layout_tweet)

        // 認証済みか否かで、UIの可視状態を切り替え
        if (accessKey.length > 40 && accessSecret.length > 40) {
            linearLayoutFirst.visibility = View.GONE;
            linearLayoutTweet.visibility = View.VISIBLE;

            val iconImage = view.findViewById<ImageView>(R.id.icon_image)
            val buttonClear = view.findViewById<Button>(R.id.button_clear)
            val buttonLocate = view.findViewById<Button>(R.id.button_locate)
            val buttonTweet = view.findViewById<Button>(R.id.button_tweet)

            // EditText群に対して設定
            val editTextList = arrayOf(
                view.findViewById<EditText>(R.id.tweet_prefix),
                view.findViewById<EditText>(R.id.tweet_main),
                view.findViewById<EditText>(R.id.tweet_suffix)
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
                        buttonTweet.text = buf.toString().length.toString()
                    }
                })
            }

            // ボタンのクリックイベントを設定
            buttonClear.setOnClickListener {
                for (editTextItem in editTextList) {
                    editTextItem.text.clear()
                }
            }
            buttonLocate.setOnClickListener {
                reverseGeocode()
            }
            buttonTweet.setOnClickListener {
                view.findViewById<Button>(R.id.button_tweet).isEnabled = false

                val buf = StringBuilder().also {
                    for (editTextItem2 in editTextList) {
                        it.append(editTextItem2.text)
                    }
                }
                val tweettext = buf.toString()
                updateTweet(tweettext)

                view.findViewById<Button>(R.id.button_tweet).isEnabled = true
                editTextList[1].text.clear()
            }

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
                Log.v(packageNameString, "profileImageUrlHttps: $profileImageUrlHttps")
            }

            if (profileImageUrlHttps != "" && profileImageUrlHttps.startsWith("http")) {
                Picasso.get()
                    .load(profileImageUrlHttps)
                    .into(iconImage);
            }
        } else {
            val buttonFirst = view.findViewById<Button>(R.id.button_first)
            buttonFirst.setOnClickListener {
                getRequestToken()
            }
            linearLayoutFirst.visibility = View.VISIBLE;
            linearLayoutTweet.visibility = View.GONE;
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

        when (plugged) { // if (isDebugMode) BATTERY_PLUGGED_AC else plugged
            BATTERY_PLUGGED_AC, BATTERY_PLUGGED_USB, BATTERY_PLUGGED_WIRELESS -> {
                val lp: WindowManager.LayoutParams? = activity?.getWindow()?.getAttributes()
                if (lp != null) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                    activity?.getWindow()?.setAttributes(lp)
                    if (isDebugMode) {
                        Log.v(packageNameString, "BRIGHTNESS_OVERRIDE_FULL")
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
            Log.v(packageNameString, "sharedPreferences CK: $consumerKey")
            Log.v(packageNameString, "sharedPreferences CS: $consumerSecret")
        }
        if (consumerKey.length > 15 && consumerSecret.length > 40) {
            return Pair(consumerKey, consumerSecret)
        }

        if (isDebugMode) {
            Log.v(packageNameString, "env CK: " + getMetadata("OTOWA_CONSUMER_KEY"))
            Log.v(packageNameString, "env CS: " + getMetadata("OTOWA_CONSUMER_SECRET"))
        }
        return Pair(getMetadata("OTOWA_CONSUMER_KEY"), getMetadata("OTOWA_CONSUMER_SECRET"))
    }

    private fun getRequestToken() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val consumerKey = getCkCs().first
            val consumerSecret = getCkCs().second
            if (isDebugMode) {
                Log.v(packageNameString, "CK: $consumerKey")
                Log.v(packageNameString, "CS: $consumerSecret")
            }

            val builder = ConfigurationBuilder()
                .setDebugEnabled(true)
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
            val config = builder.build()
            val factory = TwitterFactory(config)
            oauthTwitter = factory.instance

            try {
                val requestToken = oauthTwitter.oAuthRequestToken
                withContext(Dispatchers.Main) {
                    genTwitterWebviewDialog(requestToken.authorizationURL)
                }
            } catch (e: IllegalStateException) {
                if (isDebugMode) {
                    Log.e("ERROR: ", e.toString())
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
                        "shouldOverrideUrlLoading() url: " + request?.url.toString()
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

        val uri = Uri.parse(url)
        val oauthVerifier = uri.getQueryParameter("oauth_verifier") ?: ""
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val accToken = withContext(Dispatchers.IO) {
                oauthTwitter.getOAuthAccessToken(oauthVerifier)
            }
            if (isDebugMode) {
                Log.v(packageNameString, "AK: ${accToken.token}")
                Log.v(packageNameString, "AK: ${accToken.tokenSecret}")
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

    private suspend fun showUserInfo() {
        val usr = withContext(Dispatchers.IO) { oauthTwitter.verifyCredentials() }
        if (isDebugMode) {
            Log.v("twitter", usr.name)
            Log.v("twitter", usr.screenName)
            Log.v("twitter", usr.profileImageURLHttps)
        }

        // 画面再読み込み
        withContext(Dispatchers.Main) {
            initialize()
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val usr = withContext(Dispatchers.IO) { getTw().verifyCredentials() }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            with(sharedPreferences.edit()) {
                putString(
                    "pref_profile_image_url_https",
                    usr.profileImageURLHttps
                )

                commit()
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    activity,
                    "Reloaded.",
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
                            "Tweeted: $tweettext",
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
                            "Deleted.",
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
        val latString = sharedPreferences.getString("pref_current_latitude", "")
        val lonString = sharedPreferences.getString("pref_current_longitude", "")

        if(latString != "" && lonString != "") {
            var currentLatitude = -91.0
            var currentLongitude = -181.0

            try {
                currentLatitude = parseDouble(latString)
                currentLongitude = parseDouble(lonString)

                // 測位に成功している場合
                if (currentLatitude >= -90.0 && currentLongitude >= -180.0) {
                    // 逆ジオコーディング
                    val cityName = cityDbController.searchCity(currentLatitude, currentLongitude)
                    Log.v(packageNameString, "City: $cityName")

                    if(!cityName.isNullOrEmpty()) {
                        view?.findViewById<EditText>(R.id.tweet_main)?.setText(cityName)
                    }
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.v(packageNameString, "latString: ${latString} ${e}")
                    Log.v(packageNameString, "lonString: ${lonString} ${e}")
                }
            }
        }
    }
}
