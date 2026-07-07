package com.apphud.sdk.internal.presentation.rule

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.apphud.sdk.APPHUD_PAYWALL_SCREEN_LOAD_TIMEOUT
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

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        ApphudLog.logE("[RuleWebViewActivity] Screen load timed out")
        runCatching { webView.stopLoading() }
        viewModel.onLoadTimeout()
    }

    @Volatile
    private var hasLoadedOnce = false

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

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
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
                cancelLoadTimeout()
                if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    viewModel.onScreenAppeared()
                }
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
                        val action = RuleActionParser.parse(uri.path, uri.toParamsMap())
                        if (action != RuleAction.Unknown) {
                            handleAction(action)
                            return true
                        }
                    }
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
    }

    private fun Uri.toParamsMap(): Map<String, String?> =
        queryParameterNames.associateWith { getQueryParameter(it) }

    private fun handleAction(action: RuleAction) {
        ApphudLog.log("[RuleWebViewActivity] Handling action: $action")
        when (action) {
            is RuleAction.Survey -> viewModel.processSurveyAnswer(action.question, action.answer)
            RuleAction.Dismiss -> viewModel.processDismiss()
            is RuleAction.Feedback -> readFeedbackText { text ->
                viewModel.processFeedback(action.question, text)
            }
            RuleAction.BillingIssue -> viewModel.processBillingIssue()
            is RuleAction.Purchase -> viewModel.processPurchase(action.productId, action.offerId)
            is RuleAction.ExternalLink -> handleExternalLink(action.url)
            RuleAction.IgnoreScreen -> {
                ApphudLog.log("[RuleWebViewActivity] Linked screen is not supported on Android, closing")
                viewModel.processDismiss()
            }
            RuleAction.Unknown -> Unit
        }
    }

    private fun readFeedbackText(callback: (String) -> Unit) {
        runCatching {
            webView.evaluateJavascript(
                "(function(){var e=document.getElementById('text');return e?e.textContent:'';})()",
            ) { result ->
                val text = result
                    ?.trim('"')
                    ?.replace("\\n", "\n")
                    ?.replace("\\\"", "\"")
                    ?: ""
                callback(if (text == "null") "" else text)
            }
        }.onFailure {
            ApphudLog.logE("[RuleWebViewActivity] Failed to read feedback text: ${it.message}")
            callback("")
        }
    }

    private fun handleExternalLink(externalUrl: String) {
        ApphudLog.log("[RuleWebViewActivity] External link: $externalUrl")
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.onFailure {
            ApphudLog.logE("[RuleWebViewActivity] Failed to open external link: ${it.message}")
        }
    }

    private fun openBillingSubscriptions() {
        runCatching {
            val uri = Uri.parse("https://play.google.com/store/account/subscriptions")
                .buildUpon()
                .appendQueryParameter("package", packageName)
                .build()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.onFailure {
            ApphudLog.logE("[RuleWebViewActivity] Failed to open billing subscriptions: ${it.message}")
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
                    WebViewEvent.StartLoader -> startLoader()
                    WebViewEvent.StopLoader -> stopLoader()
                    WebViewEvent.OpenBillingSubscriptions -> openBillingSubscriptions()
                    is WebViewEvent.ShowThankYouDialog -> showThankYouDialog(event.isSurvey)
                    is WebViewEvent.StartPurchase -> {
                        startPurchase(event.product, event.offerToken)
                    }
                }
            }
        }
    }

    private fun showThankYouDialog(isSurvey: Boolean) {
        val message = if (isSurvey) "Answer sent" else "Feedback sent"
        runCatching {
            AlertDialog.Builder(this)
                .setTitle("Thank you for feedback!")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    viewModel.onThankYouDialogClosed()
                }
                .show()
        }.onFailure {
            ApphudLog.logE("[RuleWebViewActivity] Failed to show dialog: ${it.message}")
            viewModel.onThankYouDialogClosed()
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

    /**
     * Shows the loader via the screen's JS `startLoader()` function, falling back to the native
     * overlay when the function is unavailable. Mirrors iOS `startLoading()`.
     */
    private fun startLoader() {
        runCatching {
            webView.evaluateJavascript(
                "(function(){if(typeof startLoader==='function'){startLoader();return 'ok';}return 'nofn';})()",
            ) { result ->
                if (result == null || !result.contains("ok")) {
                    showPurchaseLoader()
                }
            }
        }.onFailure {
            showPurchaseLoader()
        }
    }

    /**
     * Hides the loader via the screen's JS `stopLoader()` function and the native overlay.
     * Mirrors iOS `stopLoading()`.
     */
    private fun stopLoader() {
        runCatching {
            webView.evaluateJavascript(
                "(function(){if(typeof stopLoader==='function'){stopLoader();}})()",
                null,
            )
        }
        hidePurchaseLoader()
    }

    private fun showPurchaseLoader() {
        purchaseLoaderOverlay.visibility = View.VISIBLE
        webView.isEnabled = false
    }

    private fun hidePurchaseLoader() {
        purchaseLoaderOverlay.visibility = View.GONE
        webView.isEnabled = true
    }

    private fun scheduleLoadTimeout() {
        cancelLoadTimeout()
        timeoutHandler.postDelayed(timeoutRunnable, APPHUD_PAYWALL_SCREEN_LOAD_TIMEOUT)
    }

    private fun cancelLoadTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun displayContent(htmlContent: String) {
        try {
            if (!hasLoadedOnce) {
                scheduleLoadTimeout()
            }
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
