package com.apphud.sampleapp.ui.generator

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.apphud.sampleapp.R
import com.apphud.sampleapp.databinding.FragmentGeneratorBinding
import com.apphud.sampleapp.ui.utils.ResourceManager

class GeneratorFragment : Fragment() {

    private var _binding: FragmentGeneratorBinding? = null
    private val binding get() = _binding!!
    private lateinit var generatorViewModel: GeneratorViewModel

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneratorBinding.inflate(inflater, container, false)
        val root: View = binding.root

        generatorViewModel = ViewModelProvider(this).get(GeneratorViewModel::class.java)

        generatorViewModel.hexColor.observe(viewLifecycleOwner) {
            binding.labelYourColor.text = "${resources.getText(R.string.your_color_is)} ${it}"
            binding.colorView.setBackgroundColor(generatorViewModel.color)
            updateCounter()
        }

        binding.buttonGenerate.setOnClickListener {
            generatorViewModel.generateColor()
        }

        binding.buttonCopy.setOnClickListener {
            copyToClipboard()
        }

        updateCounter()
        return root
    }

    private fun copyToClipboard() {
        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(ResourceManager.getString(R.string.color),  generatorViewModel.hexColor.value)
        clipboard.setPrimaryClip(clip)

        activity?.let{
            Toast.makeText(it, ResourceManager.getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCounter(){
        binding.labelCounter.text = generatorViewModel.getCounterString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}