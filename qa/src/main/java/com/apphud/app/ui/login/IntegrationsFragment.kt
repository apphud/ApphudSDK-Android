package com.apphud.app.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.apphud.app.databinding.FragmentIntegrationsBinding

class IntegrationsFragment : Fragment() {
    private var _binding: FragmentIntegrationsBinding? = null
    private val binding get() = _binding!!
    private var viewModel: IntegrationsViewModel = IntegrationsViewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentIntegrationsBinding.inflate(inflater, container, false)
        val view = binding.root

        viewModel.setData()
        val viewAdapter = IntegrationsAdapter(viewModel, context)
        val recyclerView: RecyclerView = binding.integrationsList
        recyclerView.apply {
            adapter = viewAdapter
            addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.VERTICAL))
        }

        return view
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.save()
    }
}