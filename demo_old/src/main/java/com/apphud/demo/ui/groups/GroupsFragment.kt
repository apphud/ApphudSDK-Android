package com.apphud.demo.ui.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.apphud.demo.databinding.FragmentGroupsBinding
import com.apphud.demo.ui.utils.BaseFragment
import com.apphud.sdk.domain.ApphudPaywall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupsFragment : BaseFragment() {
    private lateinit var groupsViewModel: GroupsViewModel
    private lateinit var viewAdapter: GroupsAdapter
    private var _binding: FragmentGroupsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        groupsViewModel =
            ViewModelProvider(this).get(GroupsViewModel::class.java)

        _binding = FragmentGroupsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        viewAdapter = GroupsAdapter(groupsViewModel, context)
        viewAdapter.selectGroup = {
            Toast.makeText(activity, it.name, Toast.LENGTH_SHORT).show()
        }
        viewAdapter.selectProductId = { product ->
            // Do nothing here
        }

        val recyclerView: RecyclerView = binding.groupsList
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

    private fun updateData() {
        coroutineScope.launch {
            groupsViewModel.updateData()
            mainScope.launch {
                viewAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
