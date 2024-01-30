package com.apphud.demo.mi.ui.billingpurchases

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.mi.R
import com.xiaomi.billingclient.api.Purchase

class BillingPurchasesAdapter (private val purchasesViewModel: BillingPurchasesViewModel, private val context: Context?) : RecyclerView.Adapter<BillingPurchasesAdapter.BaseViewHolder<*>>() {
    var selectPurchase: ((account: Purchase) -> Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(
            item: T,
            position: Int,
        )
    }

    inner class HeaderViewHolder(itemView: View) : BaseViewHolder<String>(itemView) {
        private val headerTitle: TextView = itemView.findViewById(R.id.headerTitle)

        override fun bind(
            item: String,
            position: Int,
        ) {
            headerTitle.text = item
        }
    }

    inner class PurchaseViewHolder(itemView: View) : BaseViewHolder<Purchase>(itemView) {
        private val productId: TextView = itemView.findViewById(R.id.productId)
        private val purchasedAt: TextView = itemView.findViewById(R.id.purchasedAt)

        override fun bind(item: Purchase,position: Int,) {
            productId.text = item.purchaseToken
            purchasedAt.text = item.purchaseTime
            itemView.setOnClickListener {
                selectPurchase?.invoke(item)
            }
        }
    }

    /*inner class ApphudSubscriptionViewHolder(itemView: View) : BaseViewHolder<ApphudSubscription>(itemView) {
        private val productId: TextView = itemView.findViewById(R.id.productId)
        private val purchasedAt: TextView = itemView.findViewById(R.id.purchasedAt)
        private val expiresAt: TextView = itemView.findViewById(R.id.expiresAt)
        private val status: TextView = itemView.findViewById(R.id.status)

        override fun bind(
            item: ApphudSubscription,
            position: Int,
        ) {
            productId.text = item.productId
            purchasedAt.text = item.startedAt?.let { convertLongToTime(it) } ?: run { "" }
            expiresAt.text = convertLongToTime(item.expiresAt)
            status.text = item.status.name
            if (item.status.name.equals("expired", true)) {
                status.setBackgroundResource(R.color.red)
            } else {
                status.setBackgroundResource(R.color.green)
            }
            itemView.setOnClickListener {
                selectSubscription?.invoke(item)
            }
        }
    }*/

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PURCHASE = 1
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_HEADER -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.list_item_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_PURCHASE -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.list_item_purchase, parent, false)
                PurchaseViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<*>,
        position: Int,
    ) {
        val element = purchasesViewModel.items[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(element as String, position)
            is PurchaseViewHolder -> holder.bind(element as Purchase, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (purchasesViewModel.items[position]) {
            is String -> TYPE_HEADER
            is Purchase -> TYPE_PURCHASE
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = purchasesViewModel.items.size
}
