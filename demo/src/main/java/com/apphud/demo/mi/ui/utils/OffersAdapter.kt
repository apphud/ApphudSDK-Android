package com.apphud.demo.mi.ui.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.mi.R
import com.apphud.sdk.domain.SubscriptionOfferDetails

class OffersAdapter (private val offersViewModel: OffersViewModel) : RecyclerView.Adapter<OffersAdapter.BaseViewHolder<*>>() {
    var selectedOffer: ((offer: SubscriptionOfferDetails) -> Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(
            item: T,
            position: Int,
        )
    }

    inner class OfferViewHolder(itemView: View) : BaseViewHolder<SubscriptionOfferDetails>(itemView) {
        private val labelPrice: TextView = itemView.findViewById(R.id.labelPrice)
        private val labelBasePlanId: TextView = itemView.findViewById(R.id.labelBasePlanId)
        private val labelOfferId: TextView = itemView.findViewById(R.id.labelOfferId)
        private val labelOfferTag: TextView = itemView.findViewById(R.id.labelOfferTag)
        private val labelPricingPhases: TextView = itemView.findViewById(R.id.labelPricingPhases)

        override fun bind(
            item: SubscriptionOfferDetails,
            position: Int,
        ) {
            labelPrice.text = item.pricingPhases?.pricingPhaseList?.get(0)?.formattedPrice
            labelBasePlanId.text = item.basePlanId
            labelOfferId.text = item.offerId
            labelOfferTag.text = item.offerTags.toString()

            var phases = ""
            for (phase in item.pricingPhases?.pricingPhaseList?: listOf()) {
                if (phases.isNotEmpty()) phases += "\n"
                phases += phase.formattedPrice + " (" + phase.billingPeriod + ")"
            }
            labelPricingPhases.text = phases

            itemView.setOnClickListener {
                selectedOffer?.invoke(item)
            }
        }
    }

    companion object {
        private const val OFFER = 0
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BaseViewHolder<*> {
        return when (viewType) {
            OFFER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_offer, parent, false)
                OfferViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<*>,
        position: Int,
    ) {
        val element = offersViewModel.offers[position]
        when (holder) {
            is OfferViewHolder -> holder.bind(element as SubscriptionOfferDetails, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (offersViewModel.offers[position]) {
            is SubscriptionOfferDetails ->
                return OFFER
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = offersViewModel.offers.size
}
