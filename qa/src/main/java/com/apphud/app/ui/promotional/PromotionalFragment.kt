package com.apphud.app.ui.promotional

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.apphud.app.R
import com.apphud.app.databinding.FragmentPromotionalBinding
import com.apphud.sdk.Apphud

class PromotionalFragment : Fragment() {
    private lateinit var groupsViewModel: PromotionalViewModel
    private lateinit var viewAdapter: PromotionalAdapter
    private var _binding: FragmentPromotionalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        groupsViewModel =
            ViewModelProvider(this).get(PromotionalViewModel::class.java)

        _binding = FragmentPromotionalBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = PromotionalAdapter(groupsViewModel, context)
        viewAdapter.selectGroup = {
            askUser(it.id){ days ->
                if(days > 0){
                    Apphud.grantPromotional(days, null , it){ result ->
                        Toast.makeText(activity, result.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        viewAdapter.selectProduct = {
            askUser(it.product_id) { days ->
                if(days > 0) {
                    Apphud.grantPromotional( days, it.product_id, null) { result ->
                        Toast.makeText(activity, result.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val recyclerView: RecyclerView = binding.groupsList
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

    private fun askUser(sku :String, completionHandler: (Int) -> Unit){
        val alert = AlertDialog.Builder(context)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.number_picker_dialog, null)
        alert.setTitle(R.string.grant_promotion_title)
        alert.setMessage(getString(R.string.request_promotion, sku))
        alert.setView(dialogView)

        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.dialog_number_picker)
        numberPicker.maxValue = 20
        numberPicker.minValue = 1
        numberPicker.wrapSelectorWheel = false
        alert.setPositiveButton("Done") { _, _ ->
            completionHandler(numberPicker.value)
        }
        alert.setNegativeButton("Cancel") { _, _ -> }

        val alertDialog = alert.create()
        alertDialog.show()
    }

    private fun updateData(){
        groupsViewModel.updateData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}