package com.apphud.sampleapp.ui.paywall

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.apphud.sampleapp.BaseFragment
import com.apphud.sampleapp.R
import com.apphud.sampleapp.databinding.FragmentPaywallBinding
import com.apphud.sampleapp.ui.models.ProductsReadyEvent
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import com.apphud.sampleapp.ui.utils.ResourceManager
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudProductType
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PaywallFragment: BaseFragment() {

    private var _binding: FragmentPaywallBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel :PaywallViewModel
    private lateinit var adapter : ProductsAdapter

    private var placementId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaywallBinding.inflate(inflater, container, false)
        val root: View = binding.root

        activity?.let{
            if(it.intent.hasExtra("placement_id")){
                placementId = it.intent.getStringExtra("placement_id")?:""
            }
        }

        viewModel = ViewModelProvider(this)[PaywallViewModel::class.java]
        adapter = ProductsAdapter(viewModel)
        adapter.productSelected = {
            viewModel.selectedProduct = it
            adapter.notifyDataSetChanged()
        }
        binding.productsList.adapter = adapter
        viewModel.loadProducts(Placement.getPlacementByName(placementId)){ haveProducts ->
            validate(haveProducts)
        }

        viewModel.screenColor.observe(viewLifecycleOwner) { color ->
            color?.let{
                binding.mainLayout.setBackgroundColor(Color.parseColor(it))
            }
        }

        viewModel.buttonTitle.observe(viewLifecycleOwner) { title ->
            title?.let{
                binding.buttonContinue.text = title
            }
        }

        viewModel.subTitle.observe(viewLifecycleOwner) { text ->
            text?.let{
                binding.labelPaywall.text = text
            }
        }

        binding.buttonContinue.setOnClickListener {
            viewModel.selectedProduct?.let{
                purchase(it)
            }
        }

        viewModel.getPaywallInfo(Placement.getPlacementByName(placementId))
        viewModel.placementShown(Placement.getPlacementByName(placementId))

        return root
    }

    private fun validate(hasProducts: Boolean){
        binding.labelLoading.visibility = if(hasProducts) View.GONE else View.VISIBLE
        binding.buttonContinue.isEnabled = hasProducts
        adapter.notifyDataSetChanged()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: ProductsReadyEvent) {
        viewModel.loadProducts(Placement.getPlacementByName(placementId)){ haveProducts ->
            validate(haveProducts)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.placementClosed(Placement.getPlacementByName(placementId))
    }

    private fun purchase(product: ApphudProduct){
        showProgress(true)
        activity?.let{ a->
            ApphudSdkManager.purchaseProduct(a, product) { isSuccess, error ->
                showProgress(false)
                if(isSuccess){
                    Toast.makeText(a, ResourceManager.getString(R.string.success), Toast.LENGTH_SHORT).show()
                    activity?.finish()
                }
                error?.let{
                    Toast.makeText(a, it.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
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

class ProductsAdapter(private val viewModel: PaywallViewModel) : RecyclerView.Adapter<ProductsAdapter.BaseViewHolder<*>>() {
    var productSelected: ((ApphudProduct)->Unit)? = null

    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T)
    }

    companion object {
        private const val TYPE_PRODUCT = 0
    }

    class ViewHolder(val item: View) : RecyclerView.ViewHolder(item)

    inner class ProductViewHolder(itemView: View) : BaseViewHolder<ApphudProduct>(itemView) {
        private val labelTitle: TextView = itemView.findViewById(R.id.labelTitle)
        private val labelPrice: TextView = itemView.findViewById(R.id.labelPrice)
        private val layoutHolder: View = itemView.findViewById(R.id.layoutHolder)
        override fun bind(product: ApphudProduct) {
            if(product == viewModel.selectedProduct){
                layoutHolder.setBackgroundResource(R.drawable.list_item_background_selected)
            } else {
                layoutHolder.setBackgroundResource(R.drawable.list_item_background_unselected)
            }
            var price = if(product.type() == ApphudProductType.SUBS){
                product.subscriptionOfferDetails()?.let {
                    if(it.isNotEmpty()){
                        val period = period(it[0].pricingPhases?.pricingPhaseList?.get(0)?.billingPeriod?:"")
                        val result =  it[0].pricingPhases?.pricingPhaseList?.get(0)?.formattedPrice?:"N/A"
                        result + period
                    } else {
                        ResourceManager.getString(R.string.loading)
                    }
                }?: ResourceManager.getString(R.string.loading)
            } else {
                ResourceManager.getString(R.string.loading)
            }

            labelTitle.text = product.name
            labelPrice.text = price

            itemView.setOnClickListener {
                productSelected?.invoke(product)
            }
        }

        private fun period (value :String) :String{
            return when(value){
                "P1W" -> "/week"
                "P1Y"-> "/year"
                else -> ""
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<*> {
        return when (viewType) {
            TYPE_PRODUCT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_product, parent, false)
                ProductViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        val element = viewModel.productsList[position]
        when (holder) {
            is ProductViewHolder -> holder.bind(element as ApphudProduct)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val comparable = viewModel.productsList[position]
        return when (comparable) {
            is ApphudProduct -> TYPE_PRODUCT
            else -> throw IllegalArgumentException("Invalid type of data " + position)
        }
    }

    override fun getItemCount() :Int{
        return viewModel.productsList.size
    }
}