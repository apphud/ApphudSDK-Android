package com.apphud.demo.mi.ui.customer

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.mi.databinding.FragmentCustomerBinding
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.apphud.sdk.managers.HeadersInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CustomerFragment : Fragment() {
    private var _binding: FragmentCustomerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewAdapter: PaywallsAdapter
    private lateinit var paywallsViewModel: PaywallsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentCustomerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val customerId: TextView = binding.customerId
        customerId.text = Apphud.userId()

        binding.sdk.text = "v." + ApphudUtils.getSdkVersion()

        try {
            context?.let{
                val pInfo = it.packageManager.getPackageInfo( it.packageName, 0)
                binding.appVersion.text = pInfo.versionName + " (" + pInfo.versionCode + ")"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            binding.appVersion.text = ""
        }

        binding.btnSync.setOnClickListener {
            Apphud.restorePurchases { subscriptions, purchases, error ->
            }
        }

        paywallsViewModel = ViewModelProvider(this)[PaywallsViewModel::class.java]
        viewAdapter = PaywallsAdapter(paywallsViewModel, context)
        viewAdapter.selectItem = { item ->
            findNavController().navigate(CustomerFragmentDirections.actionNavCustomerToProductsFragment(item.paywall?.identifier, item.placement?.identifier))
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

        val listener =
            object : ApphudListener {
                override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {
                    Log.d("ApphudDemo", "apphudSubscriptionsUpdated")
                    updateData()
                }

                override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                    Log.d("ApphudDemo", "apphudNonRenewingPurchasesUpdated")
                    updateData()
                }

                override fun apphudDidChangeUserID(userId: String) {
                    Log.d("ApphudDemo", "apphudDidChangeUserID()")
                    // TODO handle User ID changed event
                }

                override fun userDidLoad(user: ApphudUser) {
                    Log.d("ApphudDemo", "userDidLoad(): ${user.userId}")
                    // TODO handle user registered event
                    updateData()
                }

                override fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>) {
                    Log.d("ApphudDemo", "paywallsDidFullyLoad()")
                    updateData()
                }

                override fun placementsDidFullyLoad(placements: List<ApphudPlacement>) {
                    Log.d("ApphudDemo", "placementsDidFullyLoad()")
                    updateData()
                }
            }
        Apphud.setListener(listener)

        getPaywalls()
        updateData()

        return root
    }

    private fun getPaywalls() {
        Apphud.fetchPlacements { plms, error ->
            if (plms.isEmpty() && Apphud.isFallbackMode()) {
                val paywall = Apphud.rawPaywalls().firstOrNull()
                paywall?.let {
                    val fallbackPlacement = ApphudPlacement.createCustom("fallback", paywall = it)
                    Log.d("ApphudLogs", "FALLBACK PLACEMENT: ${fallbackPlacement}")
                }
            }
            if (error != null) {
                Log.d("ApphudLogs", "Placements fetch error: ${error.billingErrorTitle()}")
            }
        }
    }

    private fun updateData() {
        _binding?.isPremiumValue?.text = if (Apphud.hasPremiumAccess()) "Premium" else "No Premium"
        GlobalScope.launch {
            paywallsViewModel.updateData()
            withContext(Dispatchers.Main) {
                viewAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
