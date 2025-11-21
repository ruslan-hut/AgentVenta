package ua.com.programmer.agentventa.documents.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import ua.com.programmer.agentventa.shared.SharedViewModel

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

        setupPaymentSpinner()

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
            binding.apply {
                orderTotalPrice.text = it.price.format(2)
                orderTotalWeight.text = it.weight.format(3)
                docPaymentType.isEnabled = it.isProcessed == 0
            }
            adapter.setClickable(it.isProcessed == 0)
            setSelectedPaymentType(it.paymentType)
        }

    }

    private fun setSelectedPaymentType(value: String) {
        val position = sharedModel.paymentTypes.indexOfFirst { it.paymentType == value }
        binding.docPaymentType.setSelection(position)
    }

    private fun setupPaymentSpinner() {

        val spinnerList = sharedModel.paymentTypes.map { it.description }
        val adapter = ArrayAdapter(requireContext(), R.layout.simple_spinner_item, spinnerList)
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        binding.docPaymentType.adapter = adapter

        binding.docPaymentType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val type = sharedModel.getPaymentType(spinnerList[position])
                viewModel.onPaymentTypeSelected(type)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}