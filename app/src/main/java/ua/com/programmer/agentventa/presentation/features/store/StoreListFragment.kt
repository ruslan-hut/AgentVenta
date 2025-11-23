package ua.com.programmer.agentventa.presentation.features.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.SimpleRecyclerBinding
import kotlin.getValue

@AndroidEntryPoint
class StoreListFragment: Fragment() {
    private val viewModel: ListViewModel by viewModels()
    private var _binding: SimpleRecyclerBinding? = null
    private val navigationArgs: StoreListFragmentArgs by navArgs()
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setOrderGuid(navigationArgs.orderGuid ?: "")
    }

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

        val adapter = Adapter { store ->
            viewModel.setStore(store) {
                view.findNavController().navigateUp()
            }
        }

        recycler.adapter = adapter

        viewModel.listItems.observe(viewLifecycleOwner) { listItems ->
            adapter.submitList(listItems)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}