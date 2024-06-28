package com.apphud.sampleapp.ui.main

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.apphud.sampleapp.R
import com.apphud.sampleapp.databinding.FragmentMainBinding
import com.apphud.sampleapp.ui.models.HasPremiumEvent
import com.apphud.sampleapp.ui.paywall.PaywallActivity
import com.apphud.sampleapp.ui.utils.Placement
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import com.apphud.sampleapp.ui.utils.ResourceManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var generatorViewModel: MainViewModel

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        val root: View = binding.root

        generatorViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        generatorViewModel.hexColor.observe(viewLifecycleOwner) {
            binding.labelYourColor.text = "${resources.getText(R.string.your_color_is)} ${it}"
            binding.colorView.setBackgroundColor(generatorViewModel.color)
            updateCounter()
        }

        generatorViewModel.showPaywall = {
            activity?.let{
                val i = Intent(it, PaywallActivity::class.java)
                i.putExtra("placement_id", Placement.main.placementId)
                startActivity(i)
            }
        }

        binding.buttonGenerate.setOnClickListener {
            generatorViewModel.generateColor()
        }

        binding.buttonCopy.setOnClickListener {
            copyToClipboard()
        }

        return root
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        updateCounter()
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHasPremiumEvent(event: HasPremiumEvent) {
        updateCounter()
    }

    private fun copyToClipboard() {
        if(ApphudSdkManager.isPremium() == true){
            val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(ResourceManager.getString(R.string.color),  generatorViewModel.hexColor.value)
            clipboard.setPrimaryClip(clip)

            activity?.let{
                Toast.makeText(it, ResourceManager.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }
        } else {
            activity?.let{
                val i = Intent(it, PaywallActivity::class.java)
                i.putExtra("placement_id", Placement.main.placementId)
                startActivity(i)
            }
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