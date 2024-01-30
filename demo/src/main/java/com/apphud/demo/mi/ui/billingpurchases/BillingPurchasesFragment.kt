package com.apphud.demo.mi.ui.billingpurchases

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.apphud.demo.mi.databinding.FragmentBillingPurchasesBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BillingPurchasesFragment : Fragment() {
    private lateinit var purchasesViewModel: BillingPurchasesViewModel
    private lateinit var viewAdapter: BillingPurchasesAdapter
    private var _binding: FragmentBillingPurchasesBinding? = null
    private val binding get() = _binding!!

    val mainScope = CoroutineScope(Dispatchers.Main)
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val errorHandler = CoroutineExceptionHandler { _, error ->
        error.message?.let {
            Log.e("BaseManager", it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        _binding = FragmentBillingPurchasesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        activity?.let{ a->
            purchasesViewModel = BillingPurchasesViewModel(a)

            viewAdapter = BillingPurchasesAdapter(purchasesViewModel, context)
            viewAdapter.selectPurchase = {
                val clipboard = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("card", it.purchaseToken)
                clipboard.setPrimaryClip(clip)

                purchasesViewModel.consume(it.purchaseToken)
                //val t = Toast.makeText(context, R.string.copied, Toast.LENGTH_LONG)
                //t.show()
            }

            binding.purchasesList.adapter = viewAdapter

            updateData()

            binding.swipeRefresh.setOnRefreshListener {
                updateData()
            }

            purchasesViewModel.addLog = { str ->
                var text = binding.logView.text.toString()
                text += "\n" + str
                binding.logView.text = text
            }
        }

        return root
    }

    private fun updateData(){
        coroutineScope.launch {
            purchasesViewModel.updateData {
                mainScope.launch {
                    _binding?.swipeRefresh?.isRefreshing = false
                    viewAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
