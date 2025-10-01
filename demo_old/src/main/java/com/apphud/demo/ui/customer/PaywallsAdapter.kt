package com.apphud.demo.ui.customer

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.R
import com.apphud.sdk.Apphud
import com.apphud.sdk.domain.ApphudPaywallScreenShowResult

class PaywallsAdapter(private val paywallsViewModel: PaywallsViewModel, private val context: Context?) :
    RecyclerView.Adapter<PaywallsAdapter.BaseViewHolder<*>>() {
    var selectItem: ((item: AdapterItem) -> Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(
            item: T,
            position: Int,
        )
    }

    inner class PaywallViewHolder(itemView: View) : BaseViewHolder<AdapterItem>(itemView) {
        private val paywallName: TextView = itemView.findViewById(R.id.paywallName)
        private val paywallIdentifier: TextView = itemView.findViewById(R.id.paywallIdentifier)
        private val paywallDefault: TextView = itemView.findViewById(R.id.paywallDefault)
        private val paywallExperiment: TextView = itemView.findViewById(R.id.paywallExperiment)
        private val paywallVariation: TextView = itemView.findViewById(R.id.paywallVariation)
        private val paywallJson: TextView = itemView.findViewById(R.id.paywallJson)
        private val layoutHolder: LinearLayout = itemView.findViewById(R.id.layoutHolder)
        private val btnShowPaywallScreen: Button = itemView.findViewById(R.id.btnShowPaywallScreen)

        override fun bind(
            item: AdapterItem,
            position: Int,
        ) {
            val paywall = item.paywall ?: item.placement?.paywall

            val experimentName = item.placement?.experimentName ?: paywall?.experimentName

            paywallName.text =
                if (item.placement != null) {
                    "${item.placement.identifier} -> ${paywall?.name}"
                } else {
                    paywall?.name
                }
            paywallIdentifier.text = "Paywall ID: " + (paywall?.identifier ?: "N/A")
            paywallDefault.text = paywall?.default.toString()
            paywallExperiment.text = item.placement?.experimentName ?: paywall?.experimentName ?: "N/A"
            paywallVariation.text = item.placement?.paywall?.variationName ?: paywall?.variationName ?: "N/A"
            paywallJson.text = if (paywall?.json != null) "true" else "false"
            experimentName?.let {
                layoutHolder.setBackgroundResource(R.color.teal_200)
                paywallDefault.setTextColor(Color.WHITE)
                paywallExperiment.setTextColor(Color.WHITE)
                paywallVariation.setTextColor(Color.WHITE)
            } ?: run {
                layoutHolder.setBackgroundResource(R.color.transparent)
                paywallDefault.setTextColor(Color.GRAY)
                paywallExperiment.setTextColor(Color.GRAY)
                paywallVariation.setTextColor(Color.GRAY)
            }

            itemView.setOnClickListener {
                paywall?.let { paywall ->
                    selectItem?.invoke(item)
                }
            }

            btnShowPaywallScreen.setOnClickListener {
                paywall?.let { paywall ->
                    showPaywallScreen(paywall)
                }
            }
        }
    }

    companion object {
        private const val TYPE_PAYWALL = 0
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_PAYWALL -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.list_item_paywall, parent, false)
                PaywallViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<*>,
        position: Int,
    ) {
        val element = paywallsViewModel.items[position]
        when (holder) {
            is PaywallViewHolder -> holder.bind(element as AdapterItem, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (paywallsViewModel.items[position]) {
            is AdapterItem -> TYPE_PAYWALL
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = paywallsViewModel.items.size

    private fun showPaywallScreen(paywall: com.apphud.sdk.domain.ApphudPaywall) {
        context?.let { ctx ->
            try {
                Apphud.showPaywallScreen(
                    context = ctx.applicationContext,
                    paywall = paywall,
                    callbacks = Apphud.ApphudPaywallScreenCallbacks(
                        onScreenShown = {
                            Log.d("PaywallsAdapter", "Paywall screen shown for paywall: ${paywall.identifier}")
                            Toast.makeText(ctx, "Paywall screen shown: ${paywall.name}", Toast.LENGTH_SHORT).show()
                        },
                        onTransactionStarted = { product ->
                            Log.d("PaywallsAdapter", "Transaction started for product: ${product?.productId}")
                            Toast.makeText(ctx, "Transaction started: ${product?.productId}", Toast.LENGTH_SHORT).show()
                        },
                        onTransactionCompleted = { result ->
                            when (result) {
                                is ApphudPaywallScreenShowResult.SubscriptionResult -> {
                                    val error = result.error
                                    if (error == null) {
                                        Log.d("PaywallsAdapter", "Subscription purchased: ${result.subscription?.productId}")
                                        Toast.makeText(ctx, "Subscription purchased!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Log.e("PaywallsAdapter", "Subscription purchase failed: ${error.message}")
                                        Toast.makeText(ctx, "Subscription purhase failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is ApphudPaywallScreenShowResult.NonRenewingResult -> {
                                    val error = result.error
                                    if (error == null) {
                                        Log.d("PaywallsAdapter", "In-App purchased: ${result.nonRenewingPurchase?.productId}")
                                        Toast.makeText(ctx, "In-App purchased!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Log.e("PaywallsAdapter", "Purchase failed: ${error.message}")
                                        Toast.makeText(ctx, "Purchase failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is ApphudPaywallScreenShowResult.TransactionError -> {
                                    Log.e("PaywallsAdapter", "Screen show error: ${result.error.message}")
                                    Toast.makeText(ctx, "Screen show error: ${result.error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onCloseButtonTapped = {
                            Log.d("PaywallsAdapter", "Paywall screen closed by user")
                            Toast.makeText(ctx, "Paywall screen closed by user", Toast.LENGTH_SHORT).show()
                        }
                    ),
                    maxTimeout = 120_000L
                )
            } catch (e: Exception) {
                Log.e("PaywallsAdapter", "Exception", e)
                Toast.makeText(ctx, "Exception: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
