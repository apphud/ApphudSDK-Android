package com.apphud.sdk.internal.presentation.rule

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.R
import com.apphud.sdk.domain.ApphudProduct
import kotlinx.coroutines.launch

@Suppress("TooGenericExceptionCaught")
internal class RuleWebViewActivity : AppCompatActivity() {

    private lateinit var viewModel: RuleViewModel
    private lateinit var webView: WebView
    private lateinit var purchaseLoaderOverlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.apphud_rule_webview_activity_layout)

        webView = findViewById(R.id.webView)
        purchaseLoaderOverlay = findViewById(R.id.purchaseLoaderOverlay)

        setupWebView()

        viewModel = ViewModelProvider(this, RuleViewModel.factory)[RuleViewModel::class.java]
        setupObservers()

        processIntent(intent)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.processBackPressed()
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            processIntent(intent)
        }
    }

    private fun processIntent(intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        ApphudLog.log("[RuleWebViewActivity] Processing intent: ruleId: $ruleId")
        viewModel.processRuleId(ruleId)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true

            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            allowFileAccess = true

            setGeolocationEnabled(true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val message = "Console: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                    ApphudLog.log(message)
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                ApphudLog.logE("[RuleWebViewActivity] WebView error: ${error?.description}, URL: ${request?.url}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                ApphudLog.log("[RuleWebViewActivity] Page loaded: $url")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                ApphudLog.logE("[RuleWebViewActivity] HTTP Error: ${errorResponse?.statusCode} - ${errorResponse?.reasonPhrase} for URL: ${request?.url}")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                request?.url?.let { uri ->
                    if (uri.scheme == "http" || uri.scheme == "https") {
                        val path = uri.path ?: ""

                        if (path == "/action") {
                            handleAction(uri)
                            return true
                        } else if (path == "/link") {
                            val externalUrl = uri.getQueryParameter("url")
                            externalUrl?.let {
                                ApphudLog.log("[RuleWebViewActivity] External link: $it")
                            }
                            return true
                        }
                    }
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
    }

    private fun handleAction(uri: Uri) {
        val type = uri.getQueryParameter("type")
        ApphudLog.log("[RuleWebViewActivity] Handling action: $type, URI: $uri")

        when (type) {
            "dismiss" -> {
                viewModel.processDismiss()
            }
            "purchase" -> {
                val productId: String? = uri.getQueryParameter("product_id")
                val offerId: String? = uri.getQueryParameter("offer_id")
                if (productId != null) {
                    ApphudLog.log("[RuleWebViewActivity] Purchase action for product: $productId, offer: $offerId")
                    viewModel.processPurchase(productId, offerId)
                }
            }
        }
    }

    private fun sendResultBroadcast(resultCode: Int) {
        val intent = Intent(RuleController.ACTION_RULE_SCREEN_RESULT).apply {
            putExtra(RuleController.EXTRA_RESULT_CODE, resultCode)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is WebViewState.Loading -> {
                            hidePurchaseLoader()
                        }
                        is WebViewState.Content -> {
                            displayContent(state.ruleScreen.htmlScreen)
                            hidePurchaseLoader()
                        }
                        is WebViewState.ContentWithPurchaseLoading -> {
                            displayContent(state.ruleScreen.htmlScreen)
                            showPurchaseLoader()
                        }
                        is WebViewState.Error -> {
                            ApphudLog.logE("[RuleWebViewActivity] Error: ${state.message}")
                            hidePurchaseLoader()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is WebViewEvent.CloseScreen -> {
                        sendResultBroadcast(RESULT_DISMISSED)
                        finishAndRemoveTask()
                    }
                    WebViewEvent.PurchaseCompleted -> {
                        sendResultBroadcast(RESULT_PURCHASE)
                        finishAndRemoveTask()
                    }
                    WebViewEvent.ProductNotFound -> {
                        Toast.makeText(this@RuleWebViewActivity, "Product or offer not found", Toast.LENGTH_SHORT).show()
                        sendResultBroadcast(RESULT_DISMISSED)
                        finishAndRemoveTask()
                    }
                    is WebViewEvent.StartPurchase -> {
                        startPurchase(event.product, event.offerToken)
                    }
                }
            }
        }
    }

    private fun startPurchase(product: ApphudProduct, offerToken: String?) {
        Apphud.purchase(
            activity = this,
            apphudProduct = product,
            offerIdToken = offerToken
        ) { result ->
            viewModel.onPurchaseResult(result)
        }
    }

    private fun showPurchaseLoader() {
        purchaseLoaderOverlay.visibility = View.VISIBLE
        webView.isEnabled = false
    }

    private fun hidePurchaseLoader() {
        purchaseLoaderOverlay.visibility = View.GONE
        webView.isEnabled = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun displayContent(htmlContent: String) {
        try {
            webView.loadDataWithBaseURL(
                "https://static.apphud.com/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        } catch (e: Exception) {
            ApphudLog.logE("[RuleWebViewActivity] Error loading HTML: ${e.message}")
            e.printStackTrace()
        }
    }

    internal companion object {
        const val RESULT_PURCHASE = 100
        const val RESULT_DISMISSED = 101
        private const val EXTRA_RULE_ID = "EXTRA_RULE_ID"

        internal fun getIntent(
            context: Context,
            ruleId: String,
        ): Intent = Intent(
            context,
            RuleWebViewActivity::class.java,
        ).apply {
            putExtra(EXTRA_RULE_ID, ruleId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (context !is Activity) {
                flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
