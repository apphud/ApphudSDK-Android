package com.apphud.app.ui.login

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apphud.app.R
import com.apphud.app.ui.managers.Integration

class IntegrationsAdapter (private val viewModel: IntegrationsViewModel, private val context: Context?) : RecyclerView.Adapter<IntegrationsAdapter.BaseViewHolder<*>>() {

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T, position: Int)
    }

    inner class IntegrationViewHolder(itemView: View) : BaseViewHolder<Integration>(itemView) {
        private val integrationTitle: TextView = itemView.findViewById(R.id.integrationTitle)
        private val integrationIcon: ImageView = itemView.findViewById(R.id.integrationIcon)
        private val switchIntegration: Switch = itemView.findViewById(R.id.switchIntegration)
        override fun bind(item: Integration, position: Int) {
            integrationTitle.text = item.title()
            integrationIcon.setImageResource(item.icon())
            switchIntegration.isChecked = viewModel.integrations.get(item) == true
            if(item.isEnabled()) {
                itemView.alpha = 1F
                itemView.setOnClickListener {
                    val state = viewModel.integrations.get(item)
                    state?.let {
                        viewModel.integrations.put(item, !it)
                        switchIntegration.isChecked = !it
                    }
                }
            }else{
                itemView.alpha = 0.4F
                itemView.setOnClickListener {
                    //do nothing
                }
            }
        }
    }

    companion object {
        private const val TYPE_INTEGRATION = 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_INTEGRATION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_integration, parent, false)
                IntegrationViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val element = viewModel.items[position]
        when (holder) {
            is IntegrationViewHolder -> holder.bind(element as Integration, position)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (viewModel.items[position]) {
            is Integration -> TYPE_INTEGRATION
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() = viewModel.items.size
}