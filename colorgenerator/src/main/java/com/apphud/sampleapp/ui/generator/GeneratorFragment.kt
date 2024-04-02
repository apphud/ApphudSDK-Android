package com.apphud.sampleapp.ui.generator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.apphud.sampleapp.databinding.FragmentGeneratorBinding

class GeneratorFragment : Fragment() {

    private var _binding: FragmentGeneratorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val generatorViewModel = ViewModelProvider(this).get(GeneratorViewModel::class.java)

        _binding = FragmentGeneratorBinding.inflate(inflater, container, false)
        val root: View = binding.root

        generatorViewModel.text.observe(viewLifecycleOwner) {
            //_binding?.textDashboard?.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}