package com.apphud.demo.ui.billing

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.databinding.FragmentBillingBinding
import com.apphud.demo.ui.utils.OffersFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.concurrent.schedule


class BillingFragment : Fragment() {

    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!

    private lateinit var productsViewModel: BillingViewModel
    private lateinit var viewAdapter: ProductsAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        productsViewModel = ViewModelProvider(this)[BillingViewModel::class.java]
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = ProductsAdapter(productsViewModel, context)
        viewAdapter.selectProduct = { product ->
            activity?.let{ activity ->
                val offers = product.subscriptionOfferDetails?.map{it.pricingPhases.pricingPhaseList[0].formattedPrice}
                offers?.let{ offers ->

                    product.subscriptionOfferDetails?.let {
                        val fragment = OffersFragment()
                        fragment.offers = it
                        fragment.offerSelected = { offer ->
                            productsViewModel.buy(
                                productDetails = product,
                                currentPurchases = null,
                                activity = activity,
                                offerIdToken = offer.offerToken
                            )
                        }
                        fragment.apply {
                            show(activity.supportFragmentManager, tag)
                        }
                    }
                }?: run {
                    productsViewModel.buy(
                        productDetails = product,
                        currentPurchases = null,
                        activity = activity,
                        offerIdToken = null
                    )
                }
            }
        }

        val recyclerView: RecyclerView = binding.productsList
        recyclerView.layoutManager = GridLayoutManager(activity,2)
        recyclerView.adapter = viewAdapter

        activity?.let{
            productsViewModel.billingConnectionState.observe(it) { isConnected ->
                if (isConnected) {
                    Timer("Update", false).schedule(500) {
                        GlobalScope.launch(Dispatchers.Main) {
                            productsViewModel.updateData()
                            viewAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        return root
    }


    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: RefreshEvent?) {
        productsViewModel.updateData()
        viewAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}