package com.apphud.sdk.internal.presentation.rule

/**
 * Parsed representation of a legacy HTML rule screen action, derived from the intercepted URL.
 *
 * On iOS the screen type is defined by URL schemes inside the HTML; on Android the same URLs are
 * intercepted by the WebView. This keeps the routing logic pure and unit-testable.
 */
internal sealed interface RuleAction {
    /** Survey option selected — always tracked on the backend, then the screen is closed on Android. */
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

    /** `/screen?id=...` without survey params — linked screen navigation is not supported on Android. */
    object IgnoreScreen : RuleAction

    /** Anything else — not a rule action. */
    object Unknown : RuleAction
}

internal object RuleActionParser {

    fun parse(path: String?, params: Map<String, String?>): RuleAction {
        if (isSurveyAnswer(params)) {
            return RuleAction.Survey(params["question"]!!, params["answer"]!!)
        }

        return when (path) {
            "/action" -> parseAction(params)
            "/link" -> params["url"]?.takeIf { it.isNotEmpty() }
                ?.let { RuleAction.ExternalLink(it) } ?: RuleAction.Unknown
            "/screen" -> RuleAction.IgnoreScreen
            "/dismiss" -> RuleAction.Dismiss
            else -> RuleAction.Unknown
        }
    }

    private fun parseAction(params: Map<String, String?>): RuleAction =
        when (params["type"]) {
            "dismiss" -> RuleAction.Dismiss
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

    /**
     * Mirrors iOS `ApphudScreenController.isSurveyAnswer`. Survey answers use `question` and
     * `answer` query params on any intercepted URL.
     */
    internal fun isSurveyAnswer(params: Map<String, String?>): Boolean {
        val question = params["question"]
        val answer = params["answer"]
        if (question.isNullOrEmpty() || answer.isNullOrEmpty()) return false
        return params["type"] != "post_feedback"
    }
}
