package com.apphud.sampleapp.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.apphud.sampleapp.R
import com.apphud.sampleapp.databinding.FragmentSettingsBinding
import com.apphud.sampleapp.ui.paywall.PaywallActivity
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import com.apphud.sampleapp.ui.utils.ResourceManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter :SettingsAdapter

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val settingsViewModel = SettingsViewModel()

        adapter = SettingsAdapter(settingsViewModel)
        adapter.restoreClick = {
            showProgress(true)
            settingsViewModel.restorePurchases { isSuccess ->
                showProgress(false)
                adapter.notifyDataSetChanged()
                activity?.let{ a->
                    if(isSuccess){
                        Toast.makeText(a, ResourceManager.getString(R.string.success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(a, ResourceManager.getString(R.string.error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        adapter.premiumClick = {
            activity?.let { a ->
                ApphudSdkManager.isPremium()?.let {
                    if (!it) {
                        val i = Intent(a, PaywallActivity::class.java)
                        i.putExtra("placement_id", Placement.settings.placementId)
                        startActivity(i)
                    } else {
                        Toast.makeText(a, ResourceManager.getString(R.string.you_are_premium), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.settingsList.adapter = adapter

        context?.let{
            val dividerItemDecoration = DividerItemDecoration(it, RecyclerView.VERTICAL)
            dividerItemDecoration.setDrawable(resources.getDrawable(R.drawable.divider, it.theme))
            binding.settingsList.addItemDecoration(dividerItemDecoration)
        }

        return root
    }

    override fun onStart() {
        super.onStart()
        adapter.notifyDataSetChanged()
    }

    private fun showProgress(isVisible :Boolean){
        if(isVisible){
            _binding?.progressView?.visibility = View.VISIBLE
        } else {
            _binding?.progressView?.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SettingsAdapter(private val settingsViewModel: SettingsViewModel) : RecyclerView.Adapter<SettingsAdapter.BaseViewHolder<*>>() {

    var restoreClick: (()->Unit)? = null
    var premiumClick: (()->Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T)
    }

    companion object {
        private const val TYPE_INFO_MIDDLE = 0
        private const val TYPE_INFO_TOP = 1
        private const val TYPE_INFO_BOTTOM = 2
        private const val TYPE_INFO_SINGLE = 3
        private const val TYPE_BUTTON_MIDDLE = 4
        private const val TYPE_BUTTON_TOP = 5
        private const val TYPE_BUTTON_BOTTOM = 6
        private const val TYPE_BUTTON_SINGLE = 7
        private const val TYPE_OFFSET = 8
    }

    class ViewHolder(val item: View) : RecyclerView.ViewHolder(item)

    inner class InfoViewHolder(itemView: View) : BaseViewHolder<SettingsInfo>(itemView) {
        private val labelTitle: TextView = itemView.findViewById(R.id.labelTitle)
        private val labelValue: TextView = itemView.findViewById(R.id.labelValue)
        override fun bind(item: SettingsInfo) {
            labelTitle.text = item.title()
            labelValue.text = item.value()
        }
    }

    inner class ButtonViewHolder(itemView: View) : BaseViewHolder<SettingsButton>(itemView) {
        private val labelTitle: TextView = itemView.findViewById(R.id.labelTitle)
        private val labelValue: TextView = itemView.findViewById(R.id.labelValue)
        override fun bind(item: SettingsButton) {
            labelTitle.text = item.title()
            labelValue.text = item.value()

            itemView.setOnClickListener {
                when(item) {
                    SettingsButton.restore -> {
                        restoreClick?.invoke()
                    }
                    SettingsButton.premium -> {
                        premiumClick?.invoke()
                    }
                }
            }
        }
    }

    inner class OffsetViewHolder(itemView: View) : BaseViewHolder<String>(itemView) {
        override fun bind(item: String) {

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_INFO_TOP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_info_top, parent, false)
                InfoViewHolder(view)
            }
            TYPE_INFO_MIDDLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_info, parent, false)
                InfoViewHolder(view)
            }
            TYPE_INFO_BOTTOM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_info_bottom, parent, false)
                InfoViewHolder(view)
            }
            TYPE_INFO_SINGLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_info_single, parent, false)
                InfoViewHolder(view)
            }
            TYPE_BUTTON_TOP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_button_top, parent, false)
                ButtonViewHolder(view)
            }
            TYPE_BUTTON_MIDDLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_button, parent, false)
                ButtonViewHolder(view)
            }
            TYPE_BUTTON_BOTTOM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_button_bottom, parent, false)
                ButtonViewHolder(view)
            }
            TYPE_BUTTON_SINGLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_settings_button_single, parent, false)
                ButtonViewHolder(view)
            }
            TYPE_OFFSET -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_offset, parent, false)
                OffsetViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val element = settingsViewModel.items[position]
        when (holder) {
            is InfoViewHolder -> holder.bind(element as SettingsInfo)
            is ButtonViewHolder -> holder.bind(element as SettingsButton)
            is OffsetViewHolder -> holder.bind(element as String)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val comparable = settingsViewModel.items.get(position)
        return when (comparable) {
            is SettingsInfo -> {
                if(position == 0){
                    if(position < settingsViewModel.items.size - 1
                        &&  settingsViewModel.items[position + 1] !is SettingsInfo){
                        return TYPE_INFO_SINGLE
                    }
                    return TYPE_INFO_TOP
                }else if(position == settingsViewModel.items.size - 1){
                    if(settingsViewModel.items[position - 1] !is SettingsInfo){
                        return TYPE_INFO_SINGLE
                    }
                    return TYPE_INFO_BOTTOM
                }else{
                    if(settingsViewModel.items[position + 1] !is SettingsInfo
                        && settingsViewModel.items[position - 1] !is SettingsInfo){
                        return TYPE_INFO_SINGLE
                    }
                    if(settingsViewModel.items[position-1] !is SettingsInfo){
                        return TYPE_INFO_TOP
                    }
                    if(settingsViewModel.items[position + 1] !is SettingsInfo){
                        return TYPE_INFO_BOTTOM
                    }
                    TYPE_INFO_MIDDLE
                }
            }
            is SettingsButton -> {
                if(position == 0){
                    if(position < settingsViewModel.items.size - 1
                        &&  settingsViewModel.items[position + 1] !is SettingsButton){
                        return TYPE_BUTTON_SINGLE
                    }
                    return TYPE_BUTTON_TOP
                }else if(position == settingsViewModel.items.size - 1){
                    if(settingsViewModel.items[position - 1] !is SettingsButton){
                        return TYPE_BUTTON_SINGLE
                    }
                    return TYPE_BUTTON_BOTTOM
                }else{
                    if(settingsViewModel.items[position + 1] !is SettingsButton
                        && settingsViewModel.items[position - 1] !is SettingsButton){
                        return TYPE_BUTTON_SINGLE
                    }
                    if(settingsViewModel.items[position-1] !is SettingsButton){
                        return TYPE_BUTTON_TOP
                    }
                    if(settingsViewModel.items[position + 1] !is SettingsButton){
                        return TYPE_BUTTON_BOTTOM
                    }
                    TYPE_BUTTON_MIDDLE
                }
            }
            is String -> TYPE_OFFSET
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() :Int{
        return settingsViewModel.items.size
    }
}