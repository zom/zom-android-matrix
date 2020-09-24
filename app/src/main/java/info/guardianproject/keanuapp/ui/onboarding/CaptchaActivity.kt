package info.guardianproject.keanuapp.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.gson.reflect.TypeToken
import info.guardianproject.keanuapp.R
import info.guardianproject.keanuapp.ui.BaseActivity
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.core.Log
import java.net.URLDecoder
import java.util.*

class CaptchaActivity : BaseActivity() {

    companion object {
        const val EXTRA_HOME_SERVER_URL = "CaptchaActivity.EXTRA_HOME_SERVER_URL"
        const val EXTRA_SITE_KEY = "CaptchaActivity.EXTRA_SITE_KEY"
        const val EXTRA_RESPONSE = "CaptchaActivity.EXTRA_RESPONSE"
        const val REQUEST_CODE = 316

        private const val RECAPTCHA_HTML = "<html> " +
                " <head> " +
                " <script type=\"text/javascript\"> " +
                " var verifyCallback = function(response) { " +  // Generic method to make a bridge between JS and the UIWebView
                " var iframe = document.createElement('iframe'); " +
                " iframe.setAttribute('src', 'js:' + JSON.stringify({'action': 'verifyCallback', 'response': response})); " +
                " document.documentElement.appendChild(iframe); " +
                " iframe.parentNode.removeChild(iframe); " +
                " iframe = null; " +
                " }; " +
                " var onloadCallback = function() { " +
                " grecaptcha.render('recaptcha_widget', { " +
                " 'sitekey' : '%s', " +
                " 'callback': verifyCallback " +
                " }); " +
                " }; " +
                " </script> " +
                " </head> " +
                " <body> " +
                " <div id=\"recaptcha_widget\"></div> " +
                " <script src=\"https://www.google.com/recaptcha/api.js?onload=onloadCallback&render=explicit\" async defer> " +
                " </script> " +
                " </body> " +
                " </html> "
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_captcha)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true

        var homeServerUrl = intent.getStringExtra(EXTRA_HOME_SERVER_URL) ?: ""
        if (!homeServerUrl.endsWith("/")) homeServerUrl += "/"

        webView.loadDataWithBaseURL(
                homeServerUrl,
                RECAPTCHA_HTML.format(intent.getStringExtra(EXTRA_SITE_KEY)),
                "text/html", "UTF-8", null)
        webView.requestLayout()

        webView.webViewClient = object : WebViewClient() {

            private fun onError(errorMessage: String) {
                runOnUiThread {
                    Toast.makeText(this@CaptchaActivity, errorMessage, Toast.LENGTH_LONG).show()

                    finish()
                }
            }

            @SuppressLint("NewApi")
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                super.onReceivedHttpError(view, request, errorResponse)

                if (request.url.toString().endsWith("favicon.ico")) {
                    // Ignore this error
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    onError(errorResponse.reasonPhrase)
                }
                else {
                    onError(errorResponse.toString())
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                super.onReceivedError(view, errorCode, description, failingUrl)

                onError(description)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (!url.startsWith("js:")) return true

                var json: String? = url.substring(3)
                var parameters: Map<String?, String?>? = null

                try {
                    json = URLDecoder.decode(json, "UTF-8")
                    parameters = JsonUtils.getBasicGson().fromJson<Map<String?, String?>>(json, object : TypeToken<HashMap<String?, String?>?>() {}.type)
                }
                catch (e: Exception) {
                    Log.e(CaptchaActivity::class.simpleName, "#shouldOverrideUrlLoading(): fromJson failed " + e.message, e)
                }

                if (parameters?.containsKey("response") ?: false
                        && "verifyCallback".equals(parameters?.get("action") ?: "")
                ) {
                    val intent = Intent()
                    intent.putExtra(EXTRA_RESPONSE, parameters?.get("response"))

                    runOnUiThread {
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }

                return true
            }
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_OK, Intent())
        finish()
    }
}