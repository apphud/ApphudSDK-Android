package com.apphud.sdk.internal.presentation.deeplink

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.apphud.sdk.ApphudLog

/**
 * Loads the Apphud connect page in a hidden 1x1 WebView to obtain a `visitorId` for deferred
 * deep link attribution. The page exposes a JavaScript `getConnectId()` function whose resolved
 * value is delivered back through the [JavascriptInterface] bridge.
 *
 * Mirrors the iOS `ApphudWebController`. Must be used on the main thread.
 */
internal class ApphudWebController {

    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var callback: ((String?) -> Unit)? = null
    private var didComplete = false

    private val timeoutRunnable = Runnable {
        ApphudLog.log("ApphudWebController timed out")
        complete(null)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun present(
        activity: Activity,
        apiKey: String,
        deviceId: String,
        connectDomainUrl: String,
        connectHost: String,
        callback: (String?) -> Unit,
    ) {
        this.callback = callback

        if (activity.isFinishing || activity.isDestroyed) {
            ApphudLog.logE("ApphudWebController: activity is not available")
            complete(null)
            return
        }

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            ApphudLog.logE("ApphudWebController: content view not found")
            complete(null)
            return
        }

        val url = Uri.parse(connectDomainUrl)
            .buildUpon()
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("device_id", deviceId)
            .appendQueryParameter("host", connectHost)
            .build()
            .toString()

        val web = WebView(activity)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.layoutParams = ViewGroup.LayoutParams(1, 1)
        web.visibility = View.INVISIBLE
        web.addJavascriptInterface(JsBridge(), JS_INTERFACE_NAME)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (didComplete) return
                fetchVisitorId()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (!shouldFailOnReceivedError(request)) {
                    ApphudLog.log("ApphudWebController ignored subresource error: ${error?.description}")
                    return
                }
                ApphudLog.logE("ApphudWebController did fail: ${error?.description}")
                complete(null)
            }
        }

        root.addView(web)
        this.webView = web

        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        ApphudLog.log("ApphudWebController started loading url $url")
        web.loadUrl(url)
    }

    private fun fetchVisitorId() {
        val web = webView ?: return
        ApphudLog.log("ApphudWebController getConnectId called")
        web.evaluateJavascript(GET_CONNECT_ID_JS, null)
    }

    internal fun shouldFailOnReceivedError(request: WebResourceRequest?): Boolean {
        return request?.isForMainFrame == true
    }

    private fun complete(visitorId: String?) {
        if (didComplete) return
        didComplete = true

        handler.removeCallbacks(timeoutRunnable)

        val cb = callback
        callback = null

        webView?.let { web ->
            web.stopLoading()
            web.webViewClient = WebViewClient()
            web.removeJavascriptInterface(JS_INTERFACE_NAME)
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        webView = null

        cb?.invoke(visitorId)
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onVisitorId(visitorId: String?) {
            val resolved = visitorId?.takeIf { it.isNotEmpty() && it != "null" }
            ApphudLog.log("ApphudWebController getConnectId returned: $resolved")
            handler.post { complete(resolved) }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val JS_INTERFACE_NAME = "ApphudConnect"
        const val GET_CONNECT_ID_JS =
            "(async () => {" +
                " try {" +
                " var id = await getConnectId();" +
                " $JS_INTERFACE_NAME.onVisitorId(id ? String(id) : null);" +
                " } catch (e) {" +
                " $JS_INTERFACE_NAME.onVisitorId(null);" +
                " }" +
                "})();"
    }
}
