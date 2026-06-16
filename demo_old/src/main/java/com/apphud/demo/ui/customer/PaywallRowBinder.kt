package com.apphud.demo.ui.customer

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.apphud.demo.R
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPaywallScreenShowResult

internal object PaywallRowBinder {

    fun bind(
        itemView: View,
        item: AdapterItem,
        context: Context?,
        onSelect: (AdapterItem) -> Unit,
    ) {
        val paywall = item.paywall ?: item.placement?.paywall
        val paywallName = itemView.findViewById<TextView>(R.id.paywallName)
        val paywallIdentifier = itemView.findViewById<TextView>(R.id.paywallIdentifier)
        val paywallDefault = itemView.findViewById<TextView>(R.id.paywallDefault)
        val paywallExperiment = itemView.findViewById<TextView>(R.id.paywallExperiment)
        val paywallVariation = itemView.findViewById<TextView>(R.id.paywallVariation)
        val paywallJson = itemView.findViewById<TextView>(R.id.paywallJson)
        val btnShowPaywallScreen = itemView.findViewById<View>(R.id.btnShowPaywallScreen)

        paywallName.text =
            if (item.placement != null) {
                "${item.placement.identifier} -> ${paywall?.name}"
            } else {
                paywall?.name
            }

        val isExperimentedPaywall = paywall?.experimentName != null || paywall?.variationName != null
        val experimentName = paywall?.experimentName ?: if (isExperimentedPaywall) Apphud.currentUser()?.experimentName else ""
        val variationName = paywall?.variationName ?: if (isExperimentedPaywall) Apphud.currentUser()?.variationName else ""

        paywallIdentifier.text = "Paywall ID: ${paywall?.identifier ?: "N/A"}"
        paywallDefault.text = paywall?.default.toString()
        paywallExperiment.text = experimentName?.takeIf { it.isNotBlank() } ?: "N/A"
        paywallVariation.text = variationName?.takeIf { it.isNotBlank() } ?: "N/A"
        paywallJson.text = if (paywall?.json != null) "true" else "false"

        val hasExperiment = isExperimentedPaywall &&
            (experimentName?.isNotBlank() == true || variationName?.isNotBlank() == true)
        styleExperimentField(paywallExperiment, hasExperiment)
        styleExperimentField(paywallVariation, hasExperiment)

        itemView.setOnClickListener {
            paywall?.let { onSelect(item) }
        }

        btnShowPaywallScreen.setOnClickListener {
            paywall?.let { showPaywallScreen(context, it) }
        }

        btnShowPaywallScreen.visibility = if (paywall?.screen != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun styleExperimentField(textView: TextView, highlight: Boolean) {
        val context = textView.context
        if (highlight) {
            textView.setTextColor(ContextCompat.getColor(context, R.color.red))
            textView.setTypeface(textView.typeface, Typeface.BOLD)
        } else {
            textView.setTextColor(ContextCompat.getColor(context, R.color.gray))
            textView.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun showPaywallScreen(context: Context?, paywall: ApphudPaywall) {
        context?.let { ctx ->
            try {
                Apphud.showPaywallScreen(
                    context = ctx.applicationContext,
                    paywall = paywall,
                    callbacks = Apphud.ApphudPaywallScreenCallbacks(
                        onScreenShown = {
                            Log.d("PaywallRowBinder", "Paywall screen shown for paywall: ${paywall.identifier}")
                            Toast.makeText(ctx, "Paywall screen shown: ${paywall.name}", Toast.LENGTH_SHORT).show()
                        },
                        onTransactionStarted = { product ->
                            Log.d("PaywallRowBinder", "Transaction started for product: ${product?.productId}")
                            Toast.makeText(ctx, "Transaction started: ${product?.productId}", Toast.LENGTH_SHORT).show()
                        },
                        onTransactionCompleted = { result ->
                            when (result) {
                                is ApphudPaywallScreenShowResult.SubscriptionResult -> {
                                    Log.d(
                                        "PaywallRowBinder",
                                        "Subscription purchased: ${result.subscription?.productId}",
                                    )
                                    Toast.makeText(ctx, "Subscription purchased!", Toast.LENGTH_SHORT).show()
                                }
                                is ApphudPaywallScreenShowResult.NonRenewingResult -> {
                                    Log.d(
                                        "PaywallRowBinder",
                                        "In-App purchased: ${result.nonRenewingPurchase?.productId}",
                                    )
                                    Toast.makeText(ctx, "In-App purchased!", Toast.LENGTH_SHORT).show()
                                }
                                is ApphudPaywallScreenShowResult.TransactionError -> {
                                    Log.e("PaywallRowBinder", "Transaction error: ${result.error.message}")
                                    Toast.makeText(
                                        ctx,
                                        "Transaction error: ${result.error.message}",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onCloseButtonTapped = {
                            Log.d("PaywallRowBinder", "Paywall screen closed by user")
                            Toast.makeText(ctx, "Paywall screen closed by user", Toast.LENGTH_SHORT).show()
                        },
                        onScreenError = { error ->
                            Log.e("PaywallRowBinder", "Screen error: ${error.message}")
                            Toast.makeText(ctx, "Screen error: ${error.message}", Toast.LENGTH_SHORT).show()
                        },
                    ),
                )
            } catch (e: Exception) {
                Log.e("PaywallRowBinder", "Exception", e)
                Toast.makeText(ctx, "Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
