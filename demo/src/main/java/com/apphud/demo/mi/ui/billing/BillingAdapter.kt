package com.apphud.demo.mi.ui.billing

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.mi.R
import com.xiaomi.billingclient.api.SkuDetails

class BillingAdapter (private val billingViewModel: BillingViewModel, private val context: Context?) : RecyclerView.Adapter<BillingAdapter.BaseViewHolder<*>>() {
    var selectProduct: ((account: SkuDetails) -> Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(
            item: T,
            position: Int,
        )
    }

    inner class ApphudProductViewHolder(itemView: View) : BaseViewHolder<SkuDetails>(itemView) {
        private val productName: TextView = itemView.findViewById(R.id.productName)
        private val productPrice: TextView = itemView.findViewById(R.id.productPrice)

        override fun bind(
            item: SkuDetails,
            position: Int,
        ) {
            productName.text = item.title
            productPrice.text = item.type
            itemView.setOnClickListener {
                selectProduct?.invoke(item)
            }
        }
    }

    companion object {
        private const val TYPE_PRODUCT = 0
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_PRODUCT -> {
                val view =
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.list_item_product_card, parent, false)
                ApphudProductViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<*>,
        position: Int,
    ) {
        val element = billingViewModel.items[position]
        when (holder) {
            is ApphudProductViewHolder -> holder.bind(element as SkuDetails, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (billingViewModel.items[position]) {
            is SkuDetails -> TYPE_PRODUCT
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = billingViewModel.items.size
}