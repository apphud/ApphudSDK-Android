package com.apphud.demo.ui.customer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.ProductDetails
import com.apphud.demo.BuildConfig
import com.apphud.demo.databinding.FragmentCustomerBinding
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.managers.HeadersInterceptor

class CustomerFragment : Fragment() {

    private var _binding: FragmentCustomerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewAdapter: PaywallsAdapter
    private lateinit var paywallsViewModel: PaywallsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentCustomerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val customerId: TextView = binding.customerId
        customerId.text = Apphud.userId()

        binding.sdk.text = "v." + HeadersInterceptor.X_SDK_VERSION
        binding.appVersion.text = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"

        binding.btnSync.setOnClickListener {
            Apphud.restorePurchases { subscriptions, purchases, error ->

            }
        }

        paywallsViewModel = ViewModelProvider(this)[PaywallsViewModel::class.java]
        viewAdapter = PaywallsAdapter(paywallsViewModel, context)
        viewAdapter.selectPaywall = { paywall ->
            findNavController().navigate(CustomerFragmentDirections.actionNavCustomerToProductsFragment(paywall.identifier))
        }

        val recyclerView: RecyclerView = binding.paywallsList
        recyclerView.apply {
            adapter = viewAdapter
            addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))
        }

        binding.toggleButton.setOnCheckedChangeListener { buttonView, isChecked ->
            paywallsViewModel.showPlacements = isChecked
            updateData()
        }

        binding.swipeRefresh.setOnRefreshListener {
            updateData()
            binding.swipeRefresh.isRefreshing = false
        }

        val listener = object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {
                Log.d("Apphud", "apphudSubscriptionsUpdated")
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                Log.d("Apphud", "apphudNonRenewingPurchasesUpdated")
            }

            override fun apphudFetchProductDetails(details: List<ProductDetails>) {
                Log.d("Apphud", "apphudFetchProductDetails()")
                //TODO handle loaded sku details
            }

            override fun apphudDidChangeUserID(userId: String) {
                Log.d("Apphud", "apphudDidChangeUserID()")
                //TODO handle User ID changed event
            }

            override fun userDidLoad() {
                Log.d("Apphud", "userDidLoad()")
                //TODO handle user registered event
            }
            
            override fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>){
                Log.d("Apphud", "paywallsDidFullyLoad()")
                updateData()
            }

            override fun placementsDidFullyLoad(placements: List<ApphudPlacement>) {
                Log.d("Apphud", "placementsDidFullyLoad()")
                updateData()
            }
        }
        Apphud.setListener(listener)

        updateData()

        return root
    }

    private fun updateData(){
        paywallsViewModel.updateData()
        viewAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}