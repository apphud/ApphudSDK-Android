package com.apphud.sdk.internal.presentation.rule

/**
 * Parsed representation of a legacy HTML rule screen action, derived from the intercepted URL.
 *
 * On iOS the screen type is defined by URL schemes inside the HTML; on Android the same URLs are
 * intercepted by the WebView. This keeps the routing logic pure and unit-testable.
 */
internal sealed interface RuleAction {
    /** `/action?type=dismiss&question=...&answer=...` — a survey option was selected. */
    data class Survey(val question: String, val answer: String) : RuleAction

    /** `/action?type=dismiss` without survey params — plain dismiss. */
    object Dismiss : RuleAction

    /** `/action?type=post_feedback&question=...` — feedback text is read from the WebView. */
    data class Feedback(val question: String) : RuleAction

    /** `/action?type=billing_issue` — open store subscriptions and dismiss. */
    object BillingIssue : RuleAction

    /** `/action?type=purchase&product_id=...&offer_id=...` — legacy HTML purchase. */
    data class Purchase(val productId: String, val offerId: String?) : RuleAction

    /** `/link?url=...` — open an external link in the browser. */
    data class ExternalLink(val url: String) : RuleAction

    /** `/screen?id=...` — multi-screen linking; intentionally not supported. */
    object IgnoreScreen : RuleAction

    /** Anything else — not a rule action. */
    object Unknown : RuleAction
}

internal object RuleActionParser {

    fun parse(path: String?, params: Map<String, String?>): RuleAction =
        when (path) {
            "/action" -> parseAction(params)
            "/link" -> params["url"]?.takeIf { it.isNotEmpty() }
                ?.let { RuleAction.ExternalLink(it) } ?: RuleAction.Unknown
            "/screen" -> RuleAction.IgnoreScreen
            else -> RuleAction.Unknown
        }

    private fun parseAction(params: Map<String, String?>): RuleAction =
        when (params["type"]) {
            "dismiss" -> {
                val question = params["question"]
                val answer = params["answer"]
                if (!question.isNullOrEmpty() && !answer.isNullOrEmpty()) {
                    RuleAction.Survey(question, answer)
                } else {
                    RuleAction.Dismiss
                }
            }
            "post_feedback" -> RuleAction.Feedback(params["question"] ?: "")
            "billing_issue" -> RuleAction.BillingIssue
            "purchase" -> {
                val productId = params["product_id"]
                if (productId != null) {
                    RuleAction.Purchase(productId, params["offer_id"])
                } else {
                    RuleAction.Unknown
                }
            }
            else -> RuleAction.Unknown
        }
}
