package com.apphud.sdk.internal.presentation

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.R
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPaywallScreen
import com.apphud.sdk.domain.Rule
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.domain.model.ApiKey
import com.apphud.sdk.internal.domain.model.RuleScreen
import com.apphud.sdk.internal.presentation.figma.FigmaWebViewActivity
import com.apphud.sdk.internal.presentation.rule.RuleWebViewActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real WebViews and session components; local HTML, no API keys or backend requests. */
@RunWith(AndroidJUnit4::class)
class ScreenSessionActivityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val html = "<html><head><title>session-fixture</title><script>var PaywallSDK={shared:function(){return {};}};</script></head><body>Session fixture</body></html>"

    @After fun cleanSession() { ServiceLocator.clearSession() }

    private fun startSession() {
        ServiceLocator.clearSession()
        ServiceLocator.initAppScope(context)
        ServiceLocator.initSessionScope(ApiKey("unused-local-fixture"), object : ApphudRuleCallback {}, {})
        runBlocking {
            ServiceLocator.instance.localRulesScreenRepository.save(
                RuleScreen(0, Rule("fixture", "screen", "Fixture", "Fixture"), html)
            ).getOrThrow()
        }
        val file = File(context.filesDir, "session-fixture.html").apply { writeText(html) }
        ServiceLocator.instance.paywallRepository.register(ApphudPaywall(
            "fixture", "Fixture", "fixture", false, null, emptyList(),
            ApphudPaywallScreen("screen", "file://${file.absolutePath}"),
            null, null, null, null, null, null,
        ))
    }

    private fun intent(figma: Boolean, sessionId: String = ServiceLocator.instance.session.screenSessionId): Intent = if (figma) {
        FigmaWebViewActivity.getIntent(context, sessionId, "fixture")
    } else {
        RuleWebViewActivity.getIntent(context, sessionId, "fixture")
    }

    private fun waitClosed(scenario: ActivityScenario<SessionBoundActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    private fun assertRendered(scenario: ActivityScenario<SessionBoundActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var title: String? = null
        while (title != "\"session-fixture\"" && SystemClock.elapsedRealtime() < deadline) {
            val latch = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.findViewById<WebView>(R.id.webView).evaluateJavascript("document.title") {
                    title = it
                    latch.countDown()
                }
            }
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            if (title != "\"session-fixture\"") SystemClock.sleep(50)
        }
        assertEquals("\"session-fixture\"", title)
        var visible = false
        while (!visible && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                visible = activity.findViewById<WebView>(R.id.webView).isShown &&
                    activity.findViewById<android.view.View>(R.id.purchaseLoaderOverlay).visibility == android.view.View.GONE
            }
            if (!visible) SystemClock.sleep(50)
        }
        assertTrue("Loaded content must be visible without a loader", visible)
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    private fun noSession(figma: Boolean) {
        ServiceLocator.clearSession()
        ActivityScenario.launch<SessionBoundActivity>(Intent(context, if (figma) FigmaWebViewActivity::class.java else RuleWebViewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { waitClosed(it) }
    }
    private fun staleSession(figma: Boolean) {
        startSession()
        val oldSessionId = ServiceLocator.instance.session.screenSessionId
        startSession()
        val oldIntent = intent(figma, oldSessionId) // A delayed launch must still carry its original session.
        ActivityScenario.launch<SessionBoundActivity>(oldIntent).use { waitClosed(it) }
    }
    private fun normalLifecycle(figma: Boolean) {
        startSession()
        ActivityScenario.launch<SessionBoundActivity>(intent(figma)).use {
            assertRendered(it)
            it.moveToState(Lifecycle.State.CREATED)
            it.moveToState(Lifecycle.State.RESUMED)
            assertRendered(it)
            it.recreate()
            assertRendered(it)
            instrumentation.runOnMainSync { ServiceLocator.clearSession() }
            waitClosed(it)
        }
    }
    private fun staleNewIntent(figma: Boolean) {
        startSession()
        val oldSessionId = ServiceLocator.instance.session.screenSessionId
        startSession()
        ActivityScenario.launch<SessionBoundActivity>(intent(figma)).use { scenario ->
            assertRendered(scenario)
            scenario.onActivity { activity ->
                // Deliver the framework callback synchronously so rejection cannot pass
                // merely because Android has not delivered a pending startActivity yet.
                instrumentation.callActivityOnNewIntent(activity,
                    intent(figma, oldSessionId).putExtra("delivery", "stale"))
                assertNull(activity.intent.getStringExtra("delivery"))
                instrumentation.callActivityOnNewIntent(activity,
                    intent(figma).putExtra("delivery", "current"))
                assertEquals("current", activity.intent.getStringExtra("delivery"))
            }
            assertRendered(scenario)
        }
    }
    private fun newSessionReplacesOldActivity(figma: Boolean) {
        startSession()
        ActivityScenario.launch<SessionBoundActivity>(intent(figma)).use { scenario ->
            assertRendered(scenario)
            val className = if (figma) FigmaWebViewActivity::class.java.name else RuleWebViewActivity::class.java.name
            val monitor = instrumentation.addMonitor(className, null, false)
            try {
                scenario.onActivity { old ->
                    startSession()
                    instrumentation.callActivityOnNewIntent(old, intent(figma).putExtra("delivery", "replacement"))
                }
                val replacement = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
                assertNotNull("A new session needs a fresh Activity and ViewModel", replacement)
                try {
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        assertEquals("replacement", replacement!!.intent.getStringExtra("delivery"))
                        assertFalse(replacement.isFinishing)
                    }
                    assertEquals(1, monitor.hits)
                    waitClosed(scenario)
                } finally {
                    instrumentation.runOnMainSync { replacement?.finish() }
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }
    @Test fun ruleCreatesFreshActivityForNewSession() = newSessionReplacesOldActivity(false)
    @Test fun figmaCreatesFreshActivityForNewSession() = newSessionReplacesOldActivity(true)
    @Test fun ruleIgnoresOldNewIntentAndAcceptsCurrent() = staleNewIntent(false)
    @Test fun figmaIgnoresOldNewIntentAndAcceptsCurrent() = staleNewIntent(true)
    @Test fun ruleWithoutSessionCloses() = noSession(false)
    @Test fun figmaWithoutSessionCloses() = noSession(true)
    @Test fun ruleFromPreviousSessionCloses() = staleSession(false)
    @Test fun figmaFromPreviousSessionCloses() = staleSession(true)
    @Test fun ruleRendersRecreatesResumesAndClosesOnLogout() = normalLifecycle(false)
    @Test fun figmaRendersRecreatesResumesAndClosesOnLogout() = normalLifecycle(true)
}
