package com.apphud.demo.ui.customer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.apphud.demo.BuildConfig
import com.apphud.demo.R
import com.apphud.demo.databinding.CustomerUserInfoSectionBinding
import com.apphud.demo.databinding.FragmentCustomerBinding
import com.apphud.demo.databinding.ViewCustomerInfoRowBinding
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser

class CustomerFragment : Fragment() {
    private var _binding: FragmentCustomerBinding? = null
    private val binding get() = _binding!!
    private lateinit var userInfo: CustomerUserInfoSectionBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentCustomerBinding.inflate(inflater, container, false)
        userInfo = binding.userInfoSection
        setupUserInfoSection()

        binding.swipeRefresh.setOnRefreshListener {
            refreshUi()
            binding.swipeRefresh.isRefreshing = false
        }

        Apphud.setListener(createListener())
        binding.root.post { refreshUi() }

        return binding.root
    }

    private fun createListener(): ApphudListener =
        object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {
                scheduleRefresh()
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                scheduleRefresh()
            }

            override fun apphudFetchProductDetails(details: List<ProductDetails>) = Unit

            override fun apphudDidChangeUserID(userId: String) {
                scheduleRefresh()
            }

            override fun userDidLoad(user: ApphudUser) {
                Log.d("ApphudDemo", "userDidLoad: ${user.userId}")
                scheduleRefresh()
            }

            override fun placementsDidFullyLoad(placements: List<ApphudPlacement>) {
                Log.d("ApphudDemo", "placementsDidFullyLoad: ${placements.size}")
                scheduleRefresh()
            }

            override fun apphudDidReceivePurchase(purchase: Purchase) {
                scheduleRefresh()
            }
        }

    private fun scheduleRefresh() {
        _binding?.root?.post { refreshUi() }
    }

    private fun setupUserInfoSection() {
        userInfo.rowUserId.infoLabel.setText(R.string.customerTitle)
        userInfo.rowSdk.infoLabel.setText(R.string.info)
        userInfo.rowAppVersion.infoLabel.setText(R.string.app_version)
        userInfo.rowPremium.infoLabel.setText(R.string.premium_status)
        userInfo.rowTargeting.infoLabel.setText(R.string.targeting_label)
        userInfo.rowExperiment.infoLabel.setText(R.string.experiment_label)
        userInfo.rowVariation.infoLabel.setText(R.string.variation_label)
        userInfo.rowSdk.infoValue.text = "DEPRECATED"
        userInfo.rowAppVersion.infoValue.text =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        styleInfoValue(userInfo.rowSdk.infoValue, isActive = false)
        userInfo.rowUserId.infoValue.apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { copyUserIdToClipboard() }
        }
    }

    private fun refreshUi() {
        val binding = _binding ?: return

        updateUserInfoFields()
        renderPlacements(binding.placementsContainer)
        binding.placementsContainer.requestLayout()
        binding.root.requestLayout()
    }

    private fun renderPlacements(container: LinearLayout) {
        container.removeAllViews()

        val placements = Apphud.rawPlacements()
            .sortedBy { it.paywall?.name ?: it.identifier.orEmpty() }

        Log.d("ApphudDemo", "renderPlacements count=${placements.size}")

        if (placements.isEmpty()) {
            container.addView(createEmptyRow(container))
            return
        }

        val inflater = LayoutInflater.from(container.context)
        placements.forEach { placement ->
            val rowView = inflater.inflate(R.layout.list_item_paywall, container, false)
            PaywallRowBinder.bind(
                itemView = rowView,
                item = AdapterItem(paywall = null, placement = placement),
                context = context,
                onSelect = { item ->
                    findNavController().navigate(
                        CustomerFragmentDirections.actionNavCustomerToProductsFragment(
                            item.paywall?.identifier,
                            item.placement?.identifier,
                        ),
                    )
                },
            )
            container.addView(rowView)
        }
    }

    private fun createEmptyRow(container: LinearLayout): View {
        return LayoutInflater.from(container.context)
            .inflate(R.layout.list_item_customer_empty, container, false)
    }

    private fun updateUserInfoFields() {
        userInfo.rowUserId.infoValue.text =
            Apphud.userId().orEmpty().ifBlank { getString(R.string.value_not_set) }

        val isPremium = Apphud.hasPremiumAccess()
        userInfo.rowPremium.infoValue.text =
            if (isPremium) getString(R.string.premium_active) else getString(R.string.premium_inactive)
        stylePremiumValue(userInfo.rowPremium, isPremium)

        val user = Apphud.currentUser()
        bindAbValue(userInfo.rowTargeting, user?.targetingName)
        bindAbValue(userInfo.rowExperiment, user?.experimentName)
        bindAbValue(userInfo.rowVariation, user?.variationName)
    }

    private fun copyUserIdToClipboard() {
        val userId = Apphud.userId() ?: return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("user_id", userId))
        Toast.makeText(requireContext(), R.string.user_id_copied, Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        scheduleRefresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
