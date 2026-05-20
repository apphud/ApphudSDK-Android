package com.apphud.demo.ui.customer

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.demo.BuildConfig
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentCustomerBinding
import com.apphud.demo.databinding.ViewCustomerInfoRowBinding
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import kotlinx.coroutines.Dispatchers
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

        setupInfoRows()
        binding.btnSync.setOnClickListener {
            Apphud.restorePurchases { _ -> }
        }

        paywallsViewModel = ViewModelProvider(this)[PaywallsViewModel::class.java]
        viewAdapter = PaywallsAdapter(paywallsViewModel, context)
        viewAdapter.selectItem = { item ->
            findNavController().navigate(
                CustomerFragmentDirections.actionNavCustomerToProductsFragment(
                    item.paywall?.identifier,
                    item.placement?.identifier
                )
            )
        }

        val recyclerView: RecyclerView = binding.paywallsList
        recyclerView.apply {
            adapter = viewAdapter
            addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))
        }

        binding.toggleButton.setOnCheckedChangeListener { _, isChecked ->
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

                override fun apphudFetchProductDetails(details: List<ProductDetails>) {
                    Log.d("ApphudDemo", "apphudFetchProductDetails()")
                }

                override fun apphudDidChangeUserID(userId: String) {
                    Log.d("ApphudDemo", "apphudDidChangeUserID()")
                    updateData()
                }

                override fun userDidLoad(user: ApphudUser) {
                    Log.d("ApphudDemo", "userDidLoad(): ${user.userId}")
                    updateData()
                }

                override fun placementsDidFullyLoad(placements: List<ApphudPlacement>) {
                    Log.d("ApphudDemo", "placementsDidFullyLoad()")
                    updateData()
                }

                override fun apphudDidReceivePurchase(purchase: Purchase) {
                    Log.d("ApphudDemo", "apphudDidReceivePurchase()")
                    updateData()
                }
            }
        Apphud.setListener(listener)

        updateData()

        return root
    }

    private fun setupInfoRows() {
        binding.rowUserId.infoLabel.setText(R.string.customerTitle)
        binding.rowSdk.infoLabel.setText(R.string.info)
        binding.rowAppVersion.infoLabel.setText(R.string.app_version)
        binding.rowPremium.infoLabel.setText(R.string.premium_status)
        binding.rowTargeting.infoLabel.setText(R.string.targeting_label)
        binding.rowExperiment.infoLabel.setText(R.string.experiment_label)
        binding.rowVariation.infoLabel.setText(R.string.variation_label)

        binding.rowSdk.infoValue.text = "DEPRECATED"
        binding.rowAppVersion.infoValue.text =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        styleInfoValue(binding.rowSdk.infoValue, isActive = false)
    }

    private fun updateData() {
        _binding?.let { binding ->
            binding.rowUserId.infoValue.text = Apphud.userId().orEmpty().ifBlank { getString(R.string.value_not_set) }

            val isPremium = Apphud.hasPremiumAccess()
            binding.rowPremium.infoValue.text =
                if (isPremium) getString(R.string.premium_active) else getString(R.string.premium_inactive)
            stylePremiumValue(binding.rowPremium, isPremium)

            val user = Apphud.currentUser()
            bindAbValue(binding.rowTargeting, user?.targetingName)
            bindAbValue(binding.rowExperiment, user?.experimentName)
            bindAbValue(binding.rowVariation, user?.variationName)
        }
        lifecycleScope.launch {
            paywallsViewModel.updateData()
            withContext(Dispatchers.Main) {
                viewAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun bindAbValue(row: ViewCustomerInfoRowBinding, value: String?) {
        val displayValue = value.orEmpty().ifBlank { getString(R.string.value_not_set) }
        row.infoValue.text = displayValue
        styleAbValue(row.infoValue, isActive = value.isNullOrBlank().not())
    }

    private fun stylePremiumValue(row: ViewCustomerInfoRowBinding, isPremium: Boolean) {
        styleInfoValue(
            textView = row.infoValue,
            isActive = isPremium,
            activeColorRes = R.color.green_dark,
        )
    }

    private fun styleAbValue(textView: TextView, isActive: Boolean) {
        styleInfoValue(
            textView = textView,
            isActive = isActive,
            activeColorRes = R.color.branding_blue_1,
        )
    }

    private fun styleInfoValue(
        textView: TextView,
        isActive: Boolean,
        activeColorRes: Int = R.color.gray,
    ) {
        val context = textView.context
        if (isActive) {
            textView.setTextColor(ContextCompat.getColor(context, activeColorRes))
            textView.setTypeface(textView.typeface, Typeface.BOLD)
        } else {
            textView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textView.setTypeface(null, Typeface.NORMAL)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
