package ua.com.programmer.agentventa.catalogs.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.dao.entity.hasLocation
import ua.com.programmer.agentventa.databinding.ClientFragmentBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class ClientInfoFragment: Fragment(){
    private val viewModel: ClientViewModel by activityViewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private var _binding: ClientFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ClientFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val clientRecycler = binding.clientImageList
        clientRecycler.layoutManager = GridLayoutManager(requireContext(), 3)
        val adapter = ClientImageAdapter(
            onItemClicked = {
                val action = ClientFragmentDirections.actionClientMenuFragmentToClientImageFragment(
                    imageGuid = it.guid
                )
                binding.root.findNavController().navigate(action)
            },
            onItemLongClicked = {
                viewModel.setDefaultImage(it)
            },
            sharedModel::loadClientImage
        )
        clientRecycler.adapter = adapter

        viewModel.client.observe(viewLifecycleOwner) { client ->
            client?.let { updateView(it) }
        }

        viewModel.clientImages.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        binding.fab.setOnClickListener {
            val action = ClientFragmentDirections.actionClientMenuFragmentToCameraFragment(
                clientGuid = viewModel.client.value?.guid ?: ""
            )
            binding.root.findNavController().navigate(action)
        }
    }

    private fun updateView(client: LClient) {
        binding.apply {
            itemName.text = client.description
            itemGroup.text = client.groupName
            itemCode.text = client.code
            itemPrice.text = client.priceType
            itemDiscount.text = client.discount.formatAsInt(1,"-", "%")
            itemBonus.text = client.bonus.format(2,"-")
            itemDebt.text = client.debt.format(2,"-.--")
            itemPhone.text = client.phone
            itemAddress.text = client.address
            itemInfo.text = client.notes //maybe something else
            itemAddressIcon.visibility = if (client.hasLocation()) View.VISIBLE else View.INVISIBLE

            addOrder.setOnClickListener {
                val action = ClientFragmentDirections.actionClientMenuFragmentToOrderFragment(
                    orderGuid = "",
                    clientGuid = client.guid,
                )
                root.findNavController().navigate(action)
            }
            addCash.setOnClickListener {
                val action = ClientFragmentDirections.actionClientMenuFragmentToCashFragment(
                    cashGuid = "",
                    clientGuid = client.guid,
                )
                root.findNavController().navigate(action)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}