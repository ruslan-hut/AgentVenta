package ua.com.programmer.agentventa.presentation.features.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.ModelContentOrderGoodsBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel

@AndroidEntryPoint
class OrderPageContent: Fragment() {

    private val viewModel: OrderViewModel by activityViewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private var _binding: ModelContentOrderGoodsBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModelContentOrderGoodsBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        setupPaymentDropdown()

        binding.fabAddGoods.setOnClickListener {
            (parentFragment?.parentFragment as? OrderFragment)?.openProductList()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = binding.goodsList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val adapter = OrderContentAdapter(
            onItemClicked = { },
            onItemLongClicked = {
                val action = OrderFragmentDirections.actionOrderFragmentToPickerFragment(
                    productGuid = it.productGuid,
                )
                view.findNavController().navigate(action)
            }
        )
        recyclerView.adapter = adapter

        viewModel.currentContent.observe(viewLifecycleOwner) {
            adapter.submitList(it )
        }
        viewModel.document.observe(viewLifecycleOwner) {
            val editable = it.isProcessed == 0
            binding.apply {
                orderTotalPrice.text = it.price.format(2)
                orderTotalWeight.text = it.weight.format(3)
                docPaymentType.isEnabled = editable
                fabAddGoods.visibility = if (editable) View.VISIBLE else View.GONE
            }
            adapter.setClickable(editable)
            setSelectedPaymentType(it.paymentType)
        }

    }

    private fun setSelectedPaymentType(value: String) {
        val position = sharedModel.paymentTypes.indexOfFirst { it.paymentType == value }
        if (position >= 0) {
            val descriptions = sharedModel.paymentTypes.map { it.description }
            binding.docPaymentType.setText(descriptions[position], false)
        }
    }

    private fun setupPaymentDropdown() {
        val descriptions = sharedModel.paymentTypes.map { it.description }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, descriptions)
        binding.docPaymentType.setAdapter(adapter)

        binding.docPaymentType.setOnItemClickListener { _, _, position, _ ->
            val type = sharedModel.getPaymentType(descriptions[position])
            viewModel.onPaymentTypeSelected(type)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}