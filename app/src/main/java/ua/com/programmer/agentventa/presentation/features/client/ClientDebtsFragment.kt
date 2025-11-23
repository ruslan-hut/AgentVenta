package ua.com.programmer.agentventa.presentation.features.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.ClientDebtsFragmentBinding
import ua.com.programmer.agentventa.extensions.format

@AndroidEntryPoint
class ClientDebtsFragment: Fragment() {
    private val viewModel: ClientViewModel by activityViewModels()
    private var _binding: ClientDebtsFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ClientDebtsFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val clientRecycler = binding.clientDocList
        clientRecycler.layoutManager = LinearLayoutManager(requireContext())

        val adapter = ClientDebtsAdapter(
            onItemClicked = { debt ->
                if (debt.hasContent == 1) {
                    val action = viewModel.client.value?.let { client ->
                        ClientFragmentDirections.actionClientMenuFragmentToDebtFragment(
                            clientGuid = client.guid,
                            docId = debt.docId
                        )
                    }
                    action?.let { it1 -> view.findNavController().navigate(it1) }
                }
            },
            onItemLongClicked = {},
        )

        clientRecycler.adapter = adapter

        viewModel.debtList.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            binding.empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.client.observe(viewLifecycleOwner) { client ->
            client?.let { binding.debt.text = it.debt.format(2, "0.00") }
        }

    }

}