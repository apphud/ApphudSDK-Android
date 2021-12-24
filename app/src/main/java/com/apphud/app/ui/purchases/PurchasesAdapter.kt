package com.apphud.app.ui.purchases

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apphud.app.R
import com.apphud.app.ui.utils.convertLongToTime
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudSubscription

class PurchasesAdapter (private val purchasesViewModel: PurchasesViewModel, private val context: Context?) : RecyclerView.Adapter<PurchasesAdapter.BaseViewHolder<*>>() {
    var selectPurchase: ((account: ApphudNonRenewingPurchase)->Unit)? = null
    var selectSubscription: ((account: ApphudSubscription)->Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T, position: Int)
    }

    inner class HeaderViewHolder(itemView: View) : BaseViewHolder<String>(itemView) {
        private val headerTitle: TextView = itemView.findViewById(R.id.headerTitle)
        override fun bind(item: String, position: Int) {
            headerTitle.text = item
        }
    }

    inner class ApphudNonRenewingPurchaseViewHolder(itemView: View) : BaseViewHolder<ApphudNonRenewingPurchase>(itemView) {
        private val productId: TextView = itemView.findViewById(R.id.productId)
        private val purchasedAt: TextView = itemView.findViewById(R.id.purchasedAt)
        override fun bind(item: ApphudNonRenewingPurchase, position: Int) {
            productId.text = item.productId
            purchasedAt.text = convertLongToTime(item.purchasedAt)
            itemView.setOnClickListener {
                selectPurchase?.invoke(item)
            }
        }
    }

    inner class ApphudSubscriptionViewHolder(itemView: View) : BaseViewHolder<ApphudSubscription>(itemView) {
        private val productId: TextView = itemView.findViewById(R.id.productId)
        private val purchasedAt: TextView = itemView.findViewById(R.id.purchasedAt)
        private val expiresAt: TextView = itemView.findViewById(R.id.expiresAt)
        private val status: TextView = itemView.findViewById(R.id.status)
        override fun bind(item: ApphudSubscription, position: Int) {
            productId.text = item.productId
            purchasedAt.text = item.startedAt?.let { convertLongToTime(it) }?:run{""}
            expiresAt.text = convertLongToTime(item.expiresAt)
            status.text = item.status.name
            if(item.status.name.equals("expired" , true)){
                status.setBackgroundResource(R.color.red)
            }else{
                status.setBackgroundResource(R.color.green)
            }
            itemView.setOnClickListener {
                selectSubscription?.invoke(item)
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PURCHASE = 1
        private const val TYPE_SUBSCRIPTION = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_PURCHASE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_purchase, parent, false)
                ApphudNonRenewingPurchaseViewHolder(view)
            }
            TYPE_SUBSCRIPTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_subscription, parent, false)
                ApphudSubscriptionViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val element = purchasesViewModel.items[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(element as String, position)
            is ApphudNonRenewingPurchaseViewHolder -> holder.bind(element as ApphudNonRenewingPurchase, position)
            is ApphudSubscriptionViewHolder -> holder.bind(element as ApphudSubscription, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (purchasesViewModel.items[position]) {
            is String -> TYPE_HEADER
            is ApphudNonRenewingPurchase -> TYPE_PURCHASE
            is ApphudSubscription -> TYPE_SUBSCRIPTION
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = purchasesViewModel.items.size
}