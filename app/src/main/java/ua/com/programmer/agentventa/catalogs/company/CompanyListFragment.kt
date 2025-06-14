package ua.com.programmer.agentventa.catalogs.company

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.SimpleRecyclerBinding
import ua.com.programmer.agentventa.shared.SharedViewModel
import kotlin.getValue

@AndroidEntryPoint
class CompanyListFragment: Fragment() {
    private val viewModel: ListViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private var _binding: SimpleRecyclerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SimpleRecyclerBinding.inflate(inflater,container,false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = binding.listRecycler
        recycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = Adapter { company ->
            viewModel.setCompany(company) {
                view.findNavController().navigateUp()
            }
        }

        recycler.adapter = adapter

        viewModel.listItems.observe(viewLifecycleOwner) { listItems ->
            adapter.submitList(listItems)
        }

        sharedModel.sharedParams.observe(this.viewLifecycleOwner) { params ->
            viewModel.setListParameters(params)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}