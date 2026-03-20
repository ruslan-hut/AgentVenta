package ua.com.programmer.agentventa.presentation.features.order

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.Order
import ua.com.programmer.agentventa.data.local.entity.getDistanceText
import ua.com.programmer.agentventa.data.local.entity.hasLocation
import ua.com.programmer.agentventa.databinding.ModelContentOrderBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import ua.com.programmer.agentventa.utility.Constants
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
        binding.docCompany.setOnClickListener {
            val action = OrderFragmentDirections.actionOrderFragmentToCompanyListFragment()
            view?.findNavController()?.navigate(action)
        }
        binding.docStore.setOnClickListener {
            val action = OrderFragmentDirections.actionOrderFragmentToStoreListFragment(
                orderGuid = viewModel.getGuid()
            )
            view?.findNavController()?.navigate(action)
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

        binding.docNotes.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.onEditNotes(binding.docNotes.text.toString())
            }
        }

        setupPriceDropdown()
        setupPaymentDropdown()

        binding.fabSave.setOnClickListener {
            (parentFragment as? OrderFragment)?.saveDocument()
        }

        return binding.root
    }

    private fun updateView(order: Order?) {
        if (order != null) {
            val options = sharedModel.options

            binding.apply {

                docNumber.text = order.number.toString()
                docDate.text = order.date
                distance.text = order.distance.toString()
                docDeliveryDate.setText(order.deliveryDate)
                docCompany.setText(order.company)
                docStore.setText(order.store)
                docClient.setText(order.clientDescription)
                docDiscount.text = order.discount.toString()
                docIsReturn.isChecked = order.isReturn == 1
                docTotalPrice.text = order.price.format(2)
                docTotalQuantity.text = order.quantity.formatAsInt(3)
                docNextPayment.text = order.nextPayment.format(2)
                docNotes.setText(order.notes)

                titleCompany.visibility = if (options.useCompanies) View.VISIBLE else View.GONE
                titleStore.visibility = if (options.useStores) View.VISIBLE else View.GONE
                docIsFiscal.visibility = if (order.isFiscal == 1) View.VISIBLE else View.GONE
                elementReturns.visibility = if (options.allowReturn) View.VISIBLE else View.GONE

                if (order.isProcessed > 0) {
                    docClient.isEnabled = false
                    docDeliveryDate.isEnabled = false
                    docPriceType.isEnabled = false
                    docPaymentType.isEnabled = false
                    docIsReturn.isEnabled = false
                    docNotes.isEnabled = false
                    fabSave.visibility = View.GONE
                } else {
                    docClient.isEnabled = true
                    docDeliveryDate.isEnabled = true
                    docPriceType.isEnabled = options.allowPriceTypeChoose
                    docPaymentType.isEnabled = true
                    docIsReturn.isEnabled = true
                    docNotes.isEnabled = true
                    fabSave.visibility = View.VISIBLE
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
        if (position >= 0) {
            val descriptions = sharedModel.priceTypes.map { it.description }
            binding.docPriceType.setText(descriptions[position], false)
        }
    }

    private fun setupPriceDropdown() {
        val descriptions = sharedModel.priceTypes.map { it.description }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, descriptions)
        binding.docPriceType.setAdapter(adapter)

        binding.docPriceType.setOnItemClickListener { _, _, position, _ ->
            val description = descriptions[position]
            val code = sharedModel.getPriceTypeCode(description)
            viewModel.onPriceTypeSelected(code, description)
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

}