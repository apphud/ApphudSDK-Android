package com.apphud.app.ui.paywalls

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.apphud.app.R
import com.apphud.app.databinding.FragmentPaywallsBinding
import com.apphud.sdk.Apphud
import com.google.gson.Gson

class PaywallsFragment : Fragment() {

    private lateinit var paywallsViewModel: PaywallsViewModel
    private var _binding: FragmentPaywallsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewAdapter: PaywallsAdapter

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        paywallsViewModel = ViewModelProvider(this).get(PaywallsViewModel::class.java)
        _binding = FragmentPaywallsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = PaywallsAdapter(paywallsViewModel, context)
        viewAdapter.selectPaywall = { paywall ->
            val json = Gson().toJson(paywall.json)
            json?.let{
                findNavController().navigate(PaywallsFragmentDirections.actionNavPaywallsToJsonFragment(it))
            }
        }
        viewAdapter.selectProduct = { product ->
            activity?.let{
                Apphud.purchase(it, product){ result ->
                    result.error?.let{ err->
                        Toast.makeText(activity, err.message, Toast.LENGTH_SHORT).show()
                    }?: run{
                        Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val recyclerView: RecyclerView = binding.paywallsList
        recyclerView.apply {
            adapter = viewAdapter
            addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))
        }

        updateData()

        binding.swipeRefresh.setOnRefreshListener {
            updateData()
            binding.swipeRefresh.isRefreshing = false
        }

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