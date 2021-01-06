package jp.gr.java_conf.ya.otowa

import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import twitter4j.Status
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    // 定数
    val CALLBACK_URL = "callback://"

    // 変数
    var is_debug_mode = false
    var packageNameString = ""

    lateinit var oauth_twitter: Twitter
    lateinit var post_twitter: Twitter

    // 初期化処理
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

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        is_debug_mode = sharedPreferences.getBoolean("pref_is_debug_mode", false)

        val accessKey = sharedPreferences.getString("pref_auth_access_key", "") ?: ""
        val accessSecret = sharedPreferences.getString("pref_auth_access_secret", "") ?: ""
        if (is_debug_mode) {
            Log.v(packageNameString, "AK: $accessKey")
            Log.v(packageNameString, "AS: $accessSecret")
        }

        // UIパーツの取得
        val buttonFirst = view.findViewById<Button>(R.id.button_first);
        val linearLayoutFirst = view.findViewById<LinearLayout>(R.id.linear_layout_first);
        val linearLayoutTweet = view.findViewById<LinearLayout>(R.id.linear_layout_tweet);

        // 認証済みか否かで、UIの可視状態を切り替え
        if (accessKey.length > 40 && accessSecret.length > 40) {
            linearLayoutFirst.visibility = View.GONE;
            linearLayoutTweet.visibility = View.VISIBLE;


            val iconImage = view.findViewById<ImageView>(R.id.icon_image)
            val buttonClear = view.findViewById<Button>(R.id.button_clear)
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
            buttonTweet.setOnClickListener {
                view.findViewById<Button>(R.id.button_tweet).isEnabled = false

                val buf = StringBuilder().also {
                    for (editTextItem2 in editTextList) {
                        it.append(editTextItem2.text)
                    }
                }
                val tweettext = buf.toString()
                if (tweettext != "") {
                    try {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {

                            //asyncOperation
                            async(Dispatchers.IO) {
                                getTw().updateStatus(tweettext)
                            }.await()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    activity,
                                    "Tweeted.",
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

                view.findViewById<Button>(R.id.button_tweet).isEnabled = true
                editTextList[1].text.clear()
            }

            iconImage.setOnLongClickListener {
                var statusList: MutableList<Status>
                var stringList: MutableList<String>
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    val tweetList = withContext(Dispatchers.IO) { getTw().userTimeline }
                    statusList = mutableListOf<Status>()
                    stringList = mutableListOf<String>()
                    for (tweet in tweetList) {
                        statusList.add(tweet)
                        stringList.add(tweet.text)
                    }

                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(activity) // FragmentではActivityを取得して生成
                            .setTitle(getString(R.string.tweet_delete_dialog))
                            .setItems(stringList.toTypedArray()) { dialog, which ->
                                // ツイートが選択された場合
                                AlertDialog.Builder(activity) // FragmentではActivityを取得して生成
                                    .setTitle(getString(R.string.tweet_delete_dialog))
                                    .setMessage(stringList[which])
                                    .setPositiveButton(getString(R.string.delete_tweet)) { dialog2, which2 ->
                                        // ツイートを削除する
                                        try {
                                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                                                async(Dispatchers.IO) {
                                                    getTw().destroyStatus(tweetList[which].id)
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
            if (is_debug_mode) {
                Log.v(packageNameString, "profileImageUrlHttps: $profileImageUrlHttps")
            }

            if (profileImageUrlHttps != "" && profileImageUrlHttps.startsWith("http")) {
                Picasso.get()
                    .load(profileImageUrlHttps)
                    .into(iconImage);
            }
        } else {
            buttonFirst.setOnClickListener {
                getRequestToken()
            }
            linearLayoutFirst.visibility = View.VISIBLE;
            linearLayoutTweet.visibility = View.GONE;
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
        if (consumerKey.length > 15 && consumerSecret.length > 40) {
            return Pair(consumerKey, consumerSecret)
        }
        return Pair(getMetadata("OTOWA_CONSUMER_KEY"), getMetadata("OTOWA_CONSUMER_SECRET"))
    }

    private fun getRequestToken() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val consumerKey = getCkCs().first
            val consumerSecret = getCkCs().second
            if (is_debug_mode) {
                Log.v(packageNameString, "CK: $consumerKey")
                Log.v(packageNameString, "CS: $consumerSecret")
            }

            val builder = ConfigurationBuilder()
                .setDebugEnabled(true)
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
            val config = builder.build()
            val factory = TwitterFactory(config)
            oauth_twitter = factory.instance

            try {
                val requestToken = oauth_twitter.oAuthRequestToken
                withContext(Dispatchers.Main) {
                    genTwitterWebviewDialog(requestToken.authorizationURL)
                }
            } catch (e: IllegalStateException) {
                if (is_debug_mode) {
                    Log.e("ERROR: ", e.toString())
                }
            }

        }
    }

    lateinit var twitterDialog: Dialog

    private suspend fun genTwitterWebviewDialog(url: String) {
        if (is_debug_mode) {
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
            if (request?.url.toString().startsWith(CALLBACK_URL)) {
                if (is_debug_mode) {
                    Log.v(
                        packageNameString,
                        "shouldOverrideUrlLoading() url: " + request?.url.toString()
                    )
                }
                getAccessToken(request?.url.toString())

                if (request?.url.toString().contains(CALLBACK_URL)) {
                    twitterDialog.dismiss()
                }
                return true
            }
            return false
        }
    }

    private fun getAccessToken(url: String) {
        if (is_debug_mode) {
            Log.v(packageNameString, "getAccessToken() url: $url")
        }

        val uri = Uri.parse(url)
        val oauthVerifier = uri.getQueryParameter("oauth_verifier") ?: ""
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val accToken = withContext(Dispatchers.IO) {
                oauth_twitter.getOAuthAccessToken(oauthVerifier)
            }
            if (is_debug_mode) {
                Log.v(packageNameString, "AK: ${accToken.token}")
                Log.v(packageNameString, "AK: ${accToken.tokenSecret}")
            }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            with(sharedPreferences.edit()) {
                putString("pref_auth_access_key", accToken.token)
                putString("pref_auth_access_secret", accToken.tokenSecret)
                putString(
                    "pref_profile_image_url_https",
                    oauth_twitter.verifyCredentials().profileImageURLHttps
                )

                commit()
            }

            showUserInfo()
        }
    }

    private suspend fun showUserInfo() {
        val usr = withContext(Dispatchers.IO) { oauth_twitter.verifyCredentials() }
        if (is_debug_mode) {
            Log.v("twitter", usr.name)
            Log.v("twitter", usr.screenName)
            Log.v("twitter", usr.profileImageURLHttps)
        }
    }

    private fun getTw(): Twitter {
        if (::post_twitter.isInitialized) {
            if (post_twitter != null) {
                return post_twitter
            }
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val builder = ConfigurationBuilder()
        builder.setDebugEnabled(is_debug_mode)
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
        post_twitter = factory.getInstance()

        return post_twitter
    }
}
