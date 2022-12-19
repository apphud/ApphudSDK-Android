package com.apphud.demo.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.SkuDetails
import com.apphud.demo.BuildConfig
import com.apphud.demo.MainActivity
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentCustomerBinding
import com.apphud.demo.ui.utils.SettingsManager
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudPurchasesRestoreCallback
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
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

        binding.switchPurchases.isChecked = SettingsManager.useApphudPurchases
        binding.switchPurchases.setOnCheckedChangeListener{ _, isChecked ->
            SettingsManager.useApphudPurchases = isChecked
        }

        binding.btnSync.setOnClickListener {
            /*(1..10).forEach{ _ ->
                Apphud.syncPurchases()
            }*/

            Apphud.syncPurchases()

            /*Apphud.restorePurchases{ subscriptions: List<ApphudSubscription>?, purchases: List<ApphudNonRenewingPurchase>?, error: ApphudError? ->
                if(subscriptions != null || purchases != null){
                    Toast.makeText(activity, "SUCCESS", Toast.LENGTH_SHORT).show()
                }

                error?.let{
                    Toast.makeText(activity, "ERROR", Toast.LENGTH_SHORT).show()
                }
            }*/
        }

        paywallsViewModel = ViewModelProvider(this)[PaywallsViewModel::class.java]
        viewAdapter = PaywallsAdapter(paywallsViewModel, context)
        viewAdapter.selectPaywall = { paywall ->
            (activity as MainActivity).paywallIdentifier = paywall.identifier
            findNavController().navigate(CustomerFragmentDirections.actionNavCustomerToProductsFragment(paywall.id))
        }

        val recyclerView: RecyclerView = binding.paywallsList
        recyclerView.apply {
            adapter = viewAdapter
            addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))
        }

        binding.swipeRefresh.setOnRefreshListener {
            updateData()
            binding.swipeRefresh.isRefreshing = false
        }

        val listener = object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {
                //TODO handle updated subscriptions
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                //TODO handle updated non renewing purchases
            }

            override fun apphudFetchSkuDetailsProducts(details: List<SkuDetails>) {
                //TODO handle loaded sku details
            }

            override fun apphudDidChangeUserID(userId: String) {
                //TODO handle User ID changed event
            }

            override fun userDidLoad() {
                //TODO handle user registered event
            }
            
            override fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>){
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