package com.apphud.sdk.internal.presentation.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleActionParserTest {

    // region survey vs dismiss

    @Test
    fun `GIVEN dismiss with question and answer EXPECT survey`() {
        val action = RuleActionParser.parse(
            "/action",
            mapOf("type" to "dismiss", "question" to "How are you?", "answer" to "Great"),
        )

        assertEquals(RuleAction.Survey("How are you?", "Great"), action)
    }

    @Test
    fun `GIVEN dismiss without survey params EXPECT dismiss`() {
        val action = RuleActionParser.parse("/action", mapOf("type" to "dismiss"))

        assertEquals(RuleAction.Dismiss, action)
    }

    @Test
    fun `GIVEN dismiss with blank answer EXPECT dismiss`() {
        val action = RuleActionParser.parse(
            "/action",
            mapOf("type" to "dismiss", "question" to "q", "answer" to ""),
        )

        assertEquals(RuleAction.Dismiss, action)
    }

    // endregion

    // region action type routing

    @Test
    fun `GIVEN post_feedback EXPECT feedback with question`() {
        val action = RuleActionParser.parse(
            "/action",
            mapOf("type" to "post_feedback", "question" to "What can we improve?"),
        )

        assertEquals(RuleAction.Feedback("What can we improve?"), action)
    }

    @Test
    fun `GIVEN post_feedback without question EXPECT feedback with empty question`() {
        val action = RuleActionParser.parse("/action", mapOf("type" to "post_feedback"))

        assertEquals(RuleAction.Feedback(""), action)
    }

    @Test
    fun `GIVEN billing_issue EXPECT billing issue`() {
        val action = RuleActionParser.parse("/action", mapOf("type" to "billing_issue"))

        assertEquals(RuleAction.BillingIssue, action)
    }

    @Test
    fun `GIVEN purchase with product EXPECT purchase`() {
        val action = RuleActionParser.parse(
            "/action",
            mapOf("type" to "purchase", "product_id" to "premium", "offer_id" to "offer1"),
        )

        assertEquals(RuleAction.Purchase("premium", "offer1"), action)
    }

    @Test
    fun `GIVEN purchase without product EXPECT unknown`() {
        val action = RuleActionParser.parse("/action", mapOf("type" to "purchase"))

        assertEquals(RuleAction.Unknown, action)
    }

    @Test
    fun `GIVEN unknown action type EXPECT unknown`() {
        val action = RuleActionParser.parse("/action", mapOf("type" to "something"))

        assertEquals(RuleAction.Unknown, action)
    }

    @Test
    fun `GIVEN unrelated path EXPECT unknown`() {
        val action = RuleActionParser.parse("/foo", emptyMap())

        assertEquals(RuleAction.Unknown, action)
    }

    // endregion

    // region screen survey

    @Test
    fun `GIVEN link with url EXPECT external link`() {
        val action = RuleActionParser.parse("/link", mapOf("url" to "https://apphud.com"))

        assertEquals(RuleAction.ExternalLink("https://apphud.com"), action)
    }

    @Test
    fun `GIVEN link without url EXPECT unknown`() {
        val action = RuleActionParser.parse("/link", emptyMap())

        assertEquals(RuleAction.Unknown, action)
    }

    @Test
    fun `GIVEN screen with question and answer EXPECT survey`() {
        val action = RuleActionParser.parse(
            "/screen",
            mapOf(
                "id" to "next-screen",
                "question" to "Why did you cancel?",
                "answer" to "Too expensive",
            ),
        )

        assertEquals(RuleAction.Survey("Why did you cancel?", "Too expensive"), action)
    }

    @Test
    fun `GIVEN any path with survey params EXPECT survey`() {
        val action = RuleActionParser.parse(
            "/unknown",
            mapOf("question" to "Q?", "answer" to "A"),
        )

        assertEquals(RuleAction.Survey("Q?", "A"), action)
    }

    @Test
    fun `GIVEN screen without survey params EXPECT ignored`() {
        val action = RuleActionParser.parse("/screen", mapOf("id" to "screen-2"))

        assertEquals(RuleAction.IgnoreScreen, action)
    }

    @Test
    fun `GIVEN dismiss path EXPECT dismiss`() {
        val action = RuleActionParser.parse("/dismiss", emptyMap())

        assertEquals(RuleAction.Dismiss, action)
    }

    // endregion
}
