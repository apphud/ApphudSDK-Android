package com.apphud.demo.ui.purchases

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.R
import com.apphud.demo.databinding.FragmentPurchasesBinding
import com.apphud.sdk.Apphud

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_purchases, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.itemId != R.id.action_sync_purchases) return false
                    Apphud.restorePurchases { _ ->
                        if (isAdded) updateData()
                    }
                    return true
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun updateData() {
        purchasesViewModel.updateData()
        viewAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
