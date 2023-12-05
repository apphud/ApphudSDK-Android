package com.apphud.demo.ui.purchases

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.databinding.FragmentPurchasesBinding

class PurchasesFragment : Fragment() {
    private lateinit var purchasesViewModel: PurchasesViewModel
    private lateinit var viewAdapter: PurchasesAdapter
    private var _binding: FragmentPurchasesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        purchasesViewModel =
            ViewModelProvider(this).get(PurchasesViewModel::class.java)

        _binding = FragmentPurchasesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = PurchasesAdapter(purchasesViewModel, context)
        viewAdapter.selectPurchase = {
            Toast.makeText(activity, it.productId, Toast.LENGTH_SHORT).show()
        }
        viewAdapter.selectSubscription = {
            Toast.makeText(activity, it.productId, Toast.LENGTH_SHORT).show()
        }

        val recyclerView: RecyclerView = binding.purchasesList
        recyclerView.apply {
            adapter = viewAdapter
        }

        updateData()

        binding.swipeRefresh.setOnRefreshListener {
            updateData()
            binding.swipeRefresh.isRefreshing = false
        }

        return root
    }

    private fun updateData()  {
        purchasesViewModel.updateData()
        viewAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
