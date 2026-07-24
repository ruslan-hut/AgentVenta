package ua.com.programmer.agentventa.presentation.features.cash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.ParentDocumentListFragmentBinding
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import ua.com.programmer.agentventa.presentation.features.client.ClientDebtsAdapter

@AndroidEntryPoint
class ParentDocumentListFragment: Fragment() {

    private val viewModel: ParentDocumentListViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private var _binding: ParentDocumentListFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ParentDocumentListFragmentBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = binding.docList
        recycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = ClientDebtsAdapter(
            onItemClicked = { debt ->
                sharedModel.selectParentDocumentAction(debt) {
                    view.findNavController().popBackStack()
                }
            },
            onItemLongClicked = {},
        )
        recycler.adapter = adapter

        viewModel.debtListItems.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            binding.empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
