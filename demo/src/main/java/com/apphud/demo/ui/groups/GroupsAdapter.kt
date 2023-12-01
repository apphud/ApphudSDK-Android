package com.apphud.demo.ui.groups

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.demo.R
import com.apphud.sdk.domain.ApphudGroup
import com.apphud.sdk.domain.ApphudProduct

class GroupsAdapter (private val groupsViewModel: GroupsViewModel, private val context: Context?) : RecyclerView.Adapter<GroupsAdapter.BaseViewHolder<*>>() {
    var selectGroup: ((account: ApphudGroup)->Unit)? = null
    var selectProduct: ((account: ApphudProduct)->Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T, position: Int)
    }

    inner class ApphudGroupViewHolder(itemView: View) : BaseViewHolder<ApphudGroup>(itemView) {
        private val groupName: TextView = itemView.findViewById(R.id.groupName)
        override fun bind(item: ApphudGroup, position: Int) {
            groupName.text = item.name
            itemView.setOnClickListener {
                selectGroup?.invoke(item)
            }
        }
    }

    inner class ApphudProductViewHolder(itemView: View) : BaseViewHolder<ApphudProduct>(itemView) {
        private val productName: TextView = itemView.findViewById(R.id.productName)
        private val productId: TextView = itemView.findViewById(R.id.productId)
        private val productPrice: TextView = itemView.findViewById(R.id.productPrice)
        override fun bind(item: ApphudProduct, position: Int) {
            productName.text = item.name
            productId.text = item.productId

            item.productDetails?.let{ details ->
                if(details.productType == BillingClient.ProductType.SUBS){
                    productPrice.text = details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice?:""
                }else{
                    productPrice.text = details.oneTimePurchaseOfferDetails?.formattedPrice?:""
                }
            }?: run{
                productPrice.text = ""
            }

            itemView.setOnClickListener {
                selectProduct?.invoke(item)
            }
        }
    }

    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_PRODUCT = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_group, parent, false)
                ApphudGroupViewHolder(view)
            }
            TYPE_PRODUCT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_product, parent, false)
                ApphudProductViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val element = groupsViewModel.items[position]
        when (holder) {
            is ApphudGroupViewHolder -> holder.bind(element as ApphudGroup, position)
            is ApphudProductViewHolder -> holder.bind(element as ApphudProduct, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (groupsViewModel.items[position]) {
            is ApphudGroup -> TYPE_GROUP
            is ApphudProduct -> TYPE_PRODUCT
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = groupsViewModel.items.size
}