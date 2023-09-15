package ua.com.programmer.agentventa.catalogs.debt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.DebtFragmentBinding

@AndroidEntryPoint
class DebtFragment: Fragment() {

    private val viewModel: DebtViewModel by viewModels()
    private val navigationArgs: DebtFragmentArgs by navArgs()
    private var _binding: DebtFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setDebtParameters(navigationArgs.clientGuid, navigationArgs.docId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DebtFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val clientRecycler = binding.itemsList
        clientRecycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = DebtContentAdapter(
            onItemClicked = {},
            onItemLongClicked = {},
        )

        clientRecycler.adapter = adapter

        viewModel.content.observe(viewLifecycleOwner) {
            updateView(it)
            adapter.submitList(it.items)
        }
        viewModel.error.observe(viewLifecycleOwner) {
            binding.errorText.text = it
            binding.errorText.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }

    }

    private fun updateView(debt: DebtViewModel.Content) {
        binding.apply {
            title.text = debt.title
            company.text = debt.company
            warehouse.text = debt.warehouse
            contractor.text = debt.contractor
            total.text = debt.total
            statusIcon.visibility = if (debt.is_processed == 1) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}