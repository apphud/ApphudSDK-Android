package com.apphud.sdk.internal.presentation.figma

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.apphud.sdk.ApphudInternal
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.R
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.purchase

import kotlinx.coroutines.launch

private class WebViewWrapper(private val webView: WebView) {
    private var currentUrl: String? = null
    private var isLoading: Boolean = false

    fun loadUrl(url: String) {
        ApphudLog.log("[WebViewWrapper] Attempting to load URL: $url")
        ApphudLog.log("[WebViewWrapper] Current URL: $currentUrl, isLoading: $isLoading")

        if (currentUrl == url && !isLoading) {
            ApphudLog.log("[WebViewWrapper] URL already loaded, skipping: $url")
            return
        }

        if (isLoading && currentUrl == url) {
            ApphudLog.log("[WebViewWrapper] Same URL is already loading, skipping: $url")
            return
        }

        currentUrl = url
        isLoading = true
        ApphudLog.log("[WebViewWrapper] Loading URL: $url")
        webView.loadUrl(url)
    }

    fun onPageStarted(url: String?) {
        ApphudLog.log("[WebViewWrapper] onPageStarted: $url")
        isLoading = true
        currentUrl = url
    }

    fun onPageFinished(url: String?) {
        ApphudLog.log("[WebViewWrapper] onPageFinished: $url")
        isLoading = false
        currentUrl = url
    }

    fun onLoadError() {
        ApphudLog.log("[WebViewWrapper] onLoadError")
        isLoading = false
    }

    fun reset() {
        ApphudLog.log("[WebViewWrapper] reset")
        currentUrl = null
        isLoading = false
    }
}

@Suppress("TooGenericExceptionCaught")
internal class FigmaWebViewActivity : AppCompatActivity() {

    private lateinit var viewModel: FigmaViewViewModel
    private lateinit var webView: WebView
    private lateinit var webViewWrapper: WebViewWrapper
    private lateinit var purchaseLoaderOverlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApphudLog.log("[FigmaWebViewActivity] onCreate, savedInstanceState: ${if (savedInstanceState != null) "exists" else "null"}")
        setContentView(R.layout.apphud_rule_webview_activity_layout)

        setupFullscreen()

        webView = findViewById(R.id.webView)
        purchaseLoaderOverlay = findViewById(R.id.purchaseLoaderOverlay)
        webViewWrapper = WebViewWrapper(webView)

        setupWebView()

        viewModel = ViewModelProvider(this, FigmaViewViewModel.factory)[FigmaViewViewModel::class.java]
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
        ApphudLog.log("[FigmaWebViewActivity] onNewIntent")
        if (intent != null) {
            setIntent(intent)
            processIntent(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        ApphudLog.log("[FigmaWebViewActivity] onStart")
    }

    override fun onResume() {
        super.onResume()
        ApphudLog.log("[FigmaWebViewActivity] onResume")
        setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        ApphudLog.log("[FigmaWebViewActivity] onPause")
    }

    override fun onStop() {
        super.onStop()
        ApphudLog.log("[FigmaWebViewActivity] onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        ApphudLog.log("[FigmaWebViewActivity] onDestroy")
        webViewWrapper.reset()
    }


    private fun processIntent(intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_PAYWALL_ID)
        val renderItemsJson = intent.getStringExtra(EXTRA_RENDER_ITEMS)
        ApphudLog.log("[RuleWebViewActivity] Processing intent: ruleId: $ruleId")
        viewModel.init(ruleId, renderItemsJson)
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

            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }

        webView.setInitialScale(100)

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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url
                ApphudLog.log("[WebViewClient] shouldOverrideUrlLoading: $url")
                if (url != null) {
                    return handleSpecialUrl(url)
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                ApphudLog.log("[WebViewClient] onPageStarted: $url")
                webViewWrapper.onPageStarted(url)
            }


            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                val errorMessage = "WebView error: ${error?.description}, URL: ${request?.url}"
                ApphudLog.logE("[WebViewClient] $errorMessage")
                webViewWrapper.onLoadError()
                viewModel.processWebViewError(errorMessage)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                ApphudLog.log("[WebViewClient] onPageFinished: $url")
                webViewWrapper.onPageFinished(url)

                val renderItemsJson = viewModel.getCurrentRenderItemsJson()
                renderItemsJson?.let { renderJson ->
                    try {
                        val jsCode = "PaywallSDK.shared().processDomMacros($renderJson)"
                        ApphudLog.log("[WebViewClient] Executing JS with escaped JSON")
                        view?.evaluateJavascript(jsCode) { result ->
                            ApphudLog.log("[WebViewClient] JS execution result: $result")
                        }
                    } catch (e: Exception) {
                        ApphudLog.logE("[WebViewClient] Error executing JS: ${e.message}")
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val errorMessage =
                    "HTTP Error: ${errorResponse?.statusCode} - ${errorResponse?.reasonPhrase} for URL: ${request?.url}"
                ApphudLog.logE("[WebViewClient] $errorMessage")
                if (errorResponse?.statusCode != 200) {
                    viewModel.processWebViewError(errorMessage)
                }
            }

        }
    }


    private fun sendResultBroadcast(resultCode: Int) {
        val intent = Intent(ACTION_FIGMA_SCREEN_RESULT).apply {
            putExtra(EXTRA_RESULT_CODE, resultCode)
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
                            ApphudLog.log("[RuleWebViewActivity] Loading state")
                            hidePurchaseLoader()
                        }
                        is WebViewState.Content -> {
                            ApphudLog.log("[RuleWebViewActivity] Content loaded for paywall: ${state.paywall.name}")
                            displayPaywallUrl(state.url)
                            hidePurchaseLoader()
                        }
                        is WebViewState.ContentWithPurchaseLoading -> {
                            ApphudLog.log("[RuleWebViewActivity] Content with purchase loading")
                            displayPaywallUrl(state.url)
                            showPurchaseLoader()
                        }
                        is WebViewState.Error -> {
                            ApphudLog.logE("[RuleWebViewActivity] Error state")
                            hidePurchaseLoader()
                        }
                        is WebViewState.WebViewLoadError -> {
                            ApphudLog.logE("[RuleWebViewActivity] WebView load error state")
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
                        sendResultBroadcast(RESULT_DISMISSED)
                        finishAndRemoveTask()
                    }

                    is WebViewEvent.RestoreCompleted -> {
                        if (event.isSuccess) {
                            sendResultBroadcast(RESULT_RESTORE_SUCCESS)
                        }
                    }
                    WebViewEvent.ShowPurchaseLoader -> {
                        showPurchaseLoader()
                    }
                    WebViewEvent.InvalidPurchaseIndex -> {
                        sendResultBroadcast(RESULT_INVALID_PURCHASE_INDEX)
                        finishAndRemoveTask()
                    }
                    is WebViewEvent.StartPurchase -> {
                        startPurchase(event.product)
                    }
                    WebViewEvent.PurchaseError -> Unit
                }
            }
        }
    }

    private fun startPurchase(product: ApphudProduct) {
        ApphudInternal.purchase(
            activity = this,
            apphudProduct = product,
            productId = null,
            offerIdToken = null,
            oldToken = null,
            replacementMode = null,
            consumableInappProduct = false,
            callback = { result ->
                viewModel.onPurchaseResult(result)
            }
        )
    }

    private fun showPurchaseLoader() {
        purchaseLoaderOverlay.visibility = View.VISIBLE
        webView.isEnabled = false
    }

    private fun hidePurchaseLoader() {
        purchaseLoaderOverlay.visibility = View.GONE
        webView.isEnabled = true
    }

    private fun handleSpecialUrl(url: Uri): Boolean {
        val lastPathComponent = url.lastPathSegment
        val urlString = url.toString()

        ApphudLog.log("[RuleWebViewActivity] Handling URL: $urlString, lastPathComponent: $lastPathComponent")

        when (lastPathComponent) {
            "restore" -> {
                ApphudLog.log("[RuleWebViewActivity] Restore action triggered")
                viewModel.processRestore()
                return true
            }
            "close" -> {
                ApphudLog.log("[RuleWebViewActivity] Close action triggered")
                viewModel.processDismiss()
                return true
            }
        }

        if (urlString.contains("/purchase/")) {
            val index = lastPathComponent
            if (!index.isNullOrEmpty()) {
                try {
                    val purchaseIndex = index.toInt()
                    if (purchaseIndex >= 0) {
                        ApphudLog.log("[RuleWebViewActivity] Purchase action triggered for index: $purchaseIndex")
                        viewModel.processPurchaseByIndex(purchaseIndex)
                    }
                } catch (e: NumberFormatException) {
                    ApphudLog.logE("[RuleWebViewActivity] Invalid purchase index: $index")
                }
            }
            return true
        }

        return false
    }

    private fun setupFullscreen() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
            }

            ApphudLog.log("[FigmaWebViewActivity] Fullscreen setup completed")
        } catch (e: Exception) {
            ApphudLog.logE("[FigmaWebViewActivity] Error setting up fullscreen: ${e.message}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun displayPaywallUrl(url: String) {
        try {
            ApphudLog.log("[RuleWebViewActivity] Loading paywall URL: $url")
            webViewWrapper.loadUrl(url)
        } catch (e: Exception) {
            ApphudLog.logE("[RuleWebViewActivity] Error loading paywall URL: ${e.message}")
            viewModel.processWebViewError("Error loading paywall URL: ${e.message}")
        }
    }

    internal companion object {
        const val RESULT_PURCHASE = 100
        const val RESULT_DISMISSED = 101
        const val RESULT_PURCHASE_ERROR = 102
        const val RESULT_RESTORE_SUCCESS = 103
        const val RESULT_RESTORE_ERROR = 104
        const val RESULT_INVALID_PURCHASE_INDEX = 105
        private const val EXTRA_PAYWALL_ID = "EXTRA_PAYWALL_ID"
        private const val EXTRA_RENDER_ITEMS = "EXTRA_RENDER_ITEMS"

        const val ACTION_FIGMA_SCREEN_RESULT = "com.apphud.sdk.FIGMA_SCREEN_RESULT"
        const val EXTRA_RESULT_CODE = "result_code"

        internal fun getIntent(
            context: Context,
            paywallId: String,
            renderItemsJson: String? = null,
        ): Intent = Intent(
            context,
            FigmaWebViewActivity::class.java,
        ).apply {
            putExtra(EXTRA_PAYWALL_ID, paywallId)
            renderItemsJson?.let { putExtra(EXTRA_RENDER_ITEMS, it) }
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (context !is Activity) {
                flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
