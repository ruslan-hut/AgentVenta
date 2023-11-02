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
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.Order
import ua.com.programmer.agentventa.dao.entity.getDistanceText
import ua.com.programmer.agentventa.dao.entity.hasLocation
import ua.com.programmer.agentventa.databinding.ModelContentOrderBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.shared.SharedViewModel
import java.util.Date

@AndroidEntryPoint
class OrderPageTitle: Fragment() {

    private val viewModel: OrderViewModel by activityViewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private var _binding: ModelContentOrderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModelContentOrderBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        viewModel.document.observe(this.viewLifecycleOwner) {
            updateView(it)
        }

        binding.docDeliveryDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker().build()
            datePicker.addOnPositiveButtonClickListener { selectedTimestamp ->
                val selectedDate = Date(selectedTimestamp)
                viewModel.setDeliveryDate(selectedDate)
            }
            datePicker.show(parentFragmentManager, "DATE_PICKER_TAG")
        }
        binding.docClient.setOnClickListener {
            val action = OrderFragmentDirections.actionOrderFragmentToClientListFragment(
                modeSelect = true,
            )
            view?.findNavController()?.navigate(action)
        }
        binding.docIsReturn.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onIsReturnClick(isChecked)
        }

        setupPriceSpinner()
        setupPaymentSpinner()

        return binding.root
    }

    private fun updateView(order: Order?) {
        if (order != null) {
            val options = sharedModel.options

            binding.apply {

                docNumber.text = order.number.toString()
                docDate.text = order.date
                distance.text = order.distance.toString()
                docDeliveryDate.text = order.deliveryDate
                docClient.text = order.clientDescription
                docDiscount.text = order.discount.toString()
                docIsReturn.isChecked = order.isReturn == 1
                docTotalPrice.text = order.price.format(2)
                docTotalQuantity.text = order.quantity.formatAsInt(3)
                docNextPayment.text = order.nextPayment.format(2)
                docNotes.text = order.notes

                docIsFiscal.visibility = if (order.isFiscal == 1) View.VISIBLE else View.GONE
                elementReturns.visibility = if (options.allowReturn) View.VISIBLE else View.GONE

                if (order.isProcessed > 0) {
                    docClient.isClickable = false
                    docDeliveryDate.isClickable = false
                    docPriceType.isEnabled = false
                    docPaymentType.isEnabled = false
                    docIsReturn.isClickable = false
                    docNextPayment.isClickable = false
                } else {
                    docClient.isClickable = true
                    docDeliveryDate.isClickable = true
                    docPriceType.isEnabled = true
                    docPaymentType.isEnabled = true
                    docIsReturn.isClickable = true
                    docNextPayment.isClickable = true
                }

                if (order.hasLocation()) {
                    distance.text = order.getDistanceText()
                } else {
                    distance.text = "?"
                }

            }
            setSelectedPriceValue(order.priceType)
            setSelectedPaymentType(order.paymentType)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setSelectedPriceValue(value: String) {
        val position = sharedModel.priceTypes.indexOfFirst { it.priceType == value }
        binding.docPriceType.setSelection(position)
    }

    private fun setupPriceSpinner() {

        val spinnerList = sharedModel.priceTypes.map { it.description }
        val adapter = ArrayAdapter(requireContext(), R.layout.price_spinner_item, spinnerList)
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)
        binding.docPriceType.adapter = adapter

        binding.docPriceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val description = spinnerList[position]
                val code = sharedModel.getPriceTypeCode(description)
                viewModel.onPriceTypeSelected(code, description)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
    }

    private fun setSelectedPaymentType(value: String) {
        val position = sharedModel.paymentTypes.indexOfFirst { it.paymentType == value }
        binding.docPaymentType.setSelection(position)
    }

    private fun setupPaymentSpinner() {

        val spinnerList = sharedModel.paymentTypes.map { it.description }
        val adapter = ArrayAdapter(requireContext(), R.layout.price_spinner_item, spinnerList)
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

}