package com.apphud.app.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.SkuDetails
import com.apphud.app.ApphudApplication
import com.apphud.app.BuildConfig
import com.apphud.app.R
import com.apphud.app.databinding.FragmentCustomerBinding
import com.apphud.app.ui.storage.StorageManager
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudPurchasesRestoreCallback
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.managers.HeadersInterceptor

class CustomerFragment : Fragment() {

    private val binding get() = _binding!!
    private var _binding: FragmentCustomerBinding? = null
    private val storage by lazy { StorageManager(ApphudApplication.applicationContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentCustomerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        storage.sandbox?.let{
            binding.sandbox.text = it
        }?:run{
            binding.sandbox.text = ""
        }

        binding.customerId.text = Apphud.userId()

        binding.appVersion.text = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"

        binding.sdk.text = HeadersInterceptor.X_SDK_VERSION

        binding.chkEmptyPaywalls.isChecked = storage.showEmptyPaywalls
        binding.chkEmptyPaywalls.setOnCheckedChangeListener{ _, state ->
            storage.showEmptyPaywalls = state
        }

        binding.chkEmptyGroups.isChecked = storage.showEmptyGroups
        binding.chkEmptyGroups.setOnCheckedChangeListener{ _, state ->
            storage.showEmptyGroups = state
        }

        binding.btnSync.setOnClickListener {
            //binding.progressBar.visibility = View.VISIBLE
            Apphud.restorePurchases(object: ApphudPurchasesRestoreCallback {
                override fun invoke(
                    subscriptions: List<ApphudSubscription>?,
                    purchases: List<ApphudNonRenewingPurchase>?,
                    error: ApphudError?
                ) {
                    //binding.progressBar.visibility = View.GONE
                    error?.let{
                        Toast.makeText(activity, "Error: " + it.message, Toast.LENGTH_LONG).show()
                    }?: run{
                        //invalidate user id
                        binding.customerId.text = Apphud.userId()

                        var count = 0
                        subscriptions?.let{
                            count += it.size
                        }
                        purchases?.let{
                            count += it.size
                        }
                        val out = getString(R.string.restore_success, count.toString())
                        Toast.makeText(activity, out, Toast.LENGTH_LONG).show()
                    }

                }

            })
        }

        binding.btnProperties.setOnClickListener {
            findNavController().navigate(CustomerFragmentDirections.actionNavCustomerToPropertiesFragment())
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
                storage.userId = userId
            }

            override fun paywallsDidLoad(paywalls: List<ApphudPaywall>) {

            }

            override fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>) {

            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}