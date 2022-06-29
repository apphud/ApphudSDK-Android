package com.apphud.demo.ui.customer

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.R
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct

class PaywallsAdapter(private val paywallsViewModel: PaywallsViewModel, private val context: Context?) : RecyclerView.Adapter<PaywallsAdapter.BaseViewHolder<*>>() {
    var selectPaywall: ((account: ApphudPaywall)->Unit)? = null
    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T, position: Int)
    }

    inner class PaywallViewHolder(itemView: View) : BaseViewHolder<ApphudPaywall>(itemView) {
        private val paywallName: TextView = itemView.findViewById(R.id.paywallName)
        private val paywallDefault: TextView = itemView.findViewById(R.id.paywallDefault)
        private val paywallExperiment: TextView = itemView.findViewById(R.id.paywallExperiment)
        private val paywallVariation: TextView = itemView.findViewById(R.id.paywallVariation)
        private val paywallJson: TextView = itemView.findViewById(R.id.paywallJson)
        private val layoutHolder: LinearLayout = itemView.findViewById(R.id.layoutHolder)

        override fun bind(item: ApphudPaywall, position: Int) {
            paywallName.text = item.name
            paywallDefault.text = item.default.toString()
            paywallExperiment.text = item.experimentName?:"-"
            paywallVariation.text = item.variationName?:"-"
            paywallJson.text = if(item.json != null) "true" else "false"
            item.experimentName?.let{
                layoutHolder.setBackgroundResource(R.color.teal_200)
                paywallDefault.setTextColor(Color.WHITE)
                paywallExperiment.setTextColor(Color.WHITE)
                paywallVariation.setTextColor(Color.WHITE)
            }?:run{
                layoutHolder.setBackgroundResource(R.color.transparent)
                paywallDefault.setTextColor(Color.GRAY)
                paywallExperiment.setTextColor(Color.GRAY)
                paywallVariation.setTextColor(Color.GRAY)
            }

            itemView.setOnClickListener {
                selectPaywall?.invoke(item)
            }
        }
    }

    companion object {
        private const val TYPE_PAYWALL = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_PAYWALL -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_paywall, parent, false)
                PaywallViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val element = paywallsViewModel.items[position]
        when (holder) {
            is PaywallViewHolder -> holder.bind(element as ApphudPaywall, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (paywallsViewModel.items[position]) {
            is ApphudPaywall -> TYPE_PAYWALL
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = paywallsViewModel.items.size
}