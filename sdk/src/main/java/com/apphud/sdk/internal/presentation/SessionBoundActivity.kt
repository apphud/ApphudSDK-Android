package com.apphud.sdk.internal.presentation

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.ServiceLocator
import com.apphud.sdk.internal.SessionComponent
import kotlinx.coroutines.launch

/** SDK screens cannot restore the session or choose credentials on behalf of the host. */
internal abstract class SessionBoundActivity : AppCompatActivity() {
    private var binding: ScreenSessionBinding? = null

    protected fun bindScreenSession(): SessionComponent? {
        val sessions = ServiceLocator.currentOrNull?.sessions
        val captured = sessions?.let { ScreenSessionBinding.create(it, intent.getStringExtra(EXTRA_SESSION)) }
        binding = captured
        if (captured == null) {
            closeStaleScreen()
            return null
        }
        lifecycleScope.launch {
            captured.awaitEnd()
            closeStaleScreen()
        }
        return captured.session.takeIf { ensureActiveScreenSession() }
    }

    protected fun ensureActiveScreenSession(): Boolean {
        if (binding?.isCurrent == true && !isFinishing && !isDestroyed) return true
        closeStaleScreen()
        return false
    }

    protected fun acceptScreenIntent(incoming: Intent): Boolean {
        val sessions = ServiceLocator.currentOrNull?.sessions
        val next = sessions?.let { ScreenSessionBinding.create(it, incoming.getStringExtra(EXTRA_SESSION)) }
        if (binding?.isCurrent == true && !isFinishing) {
            return next?.session === binding?.session
        }
        closeStaleScreen()
        if (next != null) {
            // SINGLE_TOP must not reuse a ViewModel belonging to the previous session.
            startActivity(Intent(incoming).apply { removeFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) })
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        ensureActiveScreenSession()
    }

    private fun closeStaleScreen() {
        if (!isFinishing && !isDestroyed) {
            ApphudLog.log("[Apphud screen] Closing screen without its original active session")
            // Preserve the host task. Do not send a result to a new session's receiver.
            finish()
        }
    }

    companion object {
        private const val EXTRA_SESSION = "com.apphud.sdk.SCREEN_SESSION"

        fun bindIntent(intent: Intent, screenSessionId: String) {
            intent.putExtra(EXTRA_SESSION, screenSessionId)
        }
    }
}
