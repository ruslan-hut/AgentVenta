package ua.com.programmer.agentventa.catalogs.picker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.LPrice
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.dao.entity.convertPricePerDefaultUnit
import ua.com.programmer.agentventa.dao.entity.convertQuantityPerDefaultUnit
import ua.com.programmer.agentventa.databinding.PickerFragmentBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.extensions.round
import ua.com.programmer.agentventa.shared.SharedViewModel
import ua.com.programmer.agentventa.utility.Constants
import ua.com.programmer.agentventa.utility.Utils

@AndroidEntryPoint
class PickerFragment: Fragment(), MenuProvider {

    private val viewModel: PickerViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: PickerFragmentArgs by navArgs()
    private var _binding: PickerFragmentBinding? = null
    private val binding get() = _binding
    private val utils = Utils()

    private var loadedProduct: LProduct? = null
    private var loadedPriceList: List<LPrice>? = null
    private var currentUnit = Constants.UNIT_DEFAULT
    private var currentPriceType = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedModel.sharedParams.observe(this) {
            currentPriceType = it.priceType
            viewModel.setProductParameters(navigationArgs.productGuid, it.orderGuid, currentPriceType)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PickerFragmentBinding.inflate(inflater,container,false)
        binding?.lifecycleOwner = viewLifecycleOwner
        binding?.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding?.editQuantity?.setOnEditorActionListener { _, actionId, _ -> onEditTextAction(actionId) }
        binding?.editPrice?.setOnEditorActionListener { _, actionId, _ -> onEditTextAction(actionId) }

        binding?.buttonCancel?.setOnClickListener {
            it.findNavController().navigateUp()
        }
        binding?.buttonYes?.setOnClickListener {
            saveAndExit()
        }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val priceRecycler = binding?.priceRecycler
        priceRecycler?.layoutManager = LinearLayoutManager(requireContext())

        val adapter = PriceAdapter(
            onItemClicked = {
                if (it.price > 0) {
                    binding?.editPrice?.setText(it.price.format(2))
                }
            },
            onItemLongClicked = {},
        )
        priceRecycler?.adapter = adapter

        viewModel.priceList.observe(viewLifecycleOwner) { list ->
            loadedPriceList = list
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding?.blockPriceList?.visibility = View.GONE
            } else {
                binding?.blockPriceList?.visibility = View.VISIBLE
            }
        }

        viewModel.product.observe(viewLifecycleOwner) {
            loadedProduct = it
            currentUnit = it.unitType
            updateView()
        }

        binding?.apply {
            editQuantity.requestFocus()
            editQuantity.postDelayed({
                val inputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(this.editQuantity, InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
    }

    private fun updateView() {

        val product = loadedProduct ?: LProduct()
        val options = sharedModel.options
        val currentUnitName = unitName(currentUnit, product.unit)

        binding?.apply {

            itemDescription.text = product.description
            itemCode.text = product.code
            editQuantity.hint = product.quantity.formatAsInt(3,"")
            availableQuantity.text = product.rest.formatAsInt(3,"0")
            boxIsPacked.isChecked = product.isPacked
            boxIsDemand.isChecked = product.isDemand

            itemUnit.text = currentUnitName
            restUnit.text = currentUnitName

            val priceUnitText = "${options.currency}/$currentUnitName"
            priceUnit.text = priceUnitText

            editPrice.setText(product.price.format(2))

            when (currentUnit) {
                Constants.UNIT_WEIGHT -> {
                    if (product.weight > 0) {
                        packageInfoTitle.visibility = View.VISIBLE
                        packageInfoTitle.text = getString(R.string.weight_per_unit)
                        packageInfo.text = product.weight.formatAsInt(3,"",currentUnitName)
                    } else {
                        packageInfoTitle.visibility = View.GONE
                        packageInfo.text = ""
                    }
                }
                else -> {
                    if (product.packageValue > 0) {
                        packageInfoTitle.visibility = View.VISIBLE
                        packageInfoTitle.text = getString(R.string.attr_per_package)
                        packageInfo.text = product.packageValue.formatAsInt(3,"",product.unit)
                    } else {
                        packageInfoTitle.visibility = View.GONE
                        packageInfo.text = ""
                    }
                }
            }

            if (options.usePackageMark) {
                itemIsPacked.visibility = View.VISIBLE
            } else {
                itemIsPacked.visibility = View.GONE
            }
            if (options.useDemands) {
                itemIsDemand.visibility = View.VISIBLE
            } else {
                itemIsDemand.visibility = View.GONE
            }
            if (product.basePrice > 0) {
                basePriceView.visibility = View.VISIBLE
                basePrice.text = product.basePrice.format(2)
            } else {
                basePriceView.visibility = View.GONE
            }
            (priceRecycler.adapter as PriceAdapter).setClickable(options.allowPriceTypeChoose)
        }
    }

    private fun unitName(type: String, default: String): String {
        return when (type) {
            Constants.UNIT_WEIGHT -> getString(R.string.unit_kilo)
            Constants.UNIT_PACKAGE -> getString(R.string.unit_package)
            else -> default
        }
    }

    private fun onEditTextAction(actionId: Int): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
            saveAndExit()
            return true
        }
        return false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_picker, menu)
    }

    private fun onlyPackageAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.error)
            .setMessage(R.string.error_only_package)
            .setPositiveButton(R.string.OK) { _, _ -> }
            .show()
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        val product = loadedProduct ?: LProduct()
        when (menuItem.itemId) {
            R.id.switch_unit -> {
                if (product.packageOnly) {
                    onlyPackageAlert()
                    return true
                }
                if (product.packageValue == 0.0) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_no_unit_convertion)
                        .setPositiveButton(R.string.OK) { _, _ -> }
                        .show()
                } else {
                    viewModel.switchPackageUnit()
                }
            }
            R.id.switch_weight -> {
                if (product.packageOnly) {
                    onlyPackageAlert()
                    return true
                }
                if (product.weight == 0.0 || product.indivisible) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_no_unit_weight)
                        .setPositiveButton(R.string.OK) { _, _ -> }
                        .show()
                } else {
                    viewModel.switchWeightUnit()
                }
            }
            R.id.save_item -> {
                saveAndExit()
            }
            else -> return false
        }
        return true
    }

    private fun wrongQuantityAlert(quantity: Double): Boolean {
        return if (loadedProduct?.indivisible == true && quantity % 1.0 != 0.0) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.error)
                .setMessage(R.string.error_indivisible)
                .setPositiveButton(R.string.OK) { _, _ -> }
                .show()
            true
        } else {
            false
        }
    }

    private fun saveAndExit() {
        val enteredQuantity = binding?.editQuantity?.text.toString().replace(",",".")
        val enteredPrice = binding?.editPrice?.text.toString().replace(",",".")

        var quantity = utils.round(loadedProduct?.quantity ?: 0.0, 3)

        if (enteredQuantity.isNotEmpty()) {
            quantity = if (!enteredQuantity.contains(".") && enteredQuantity.startsWith("0")) {
                "0.$enteredQuantity".toDouble()
            } else {
                enteredQuantity.toDouble()
            }
        }
        quantity = loadedProduct?.convertQuantityPerDefaultUnit(quantity, currentUnit) ?: quantity

        if (wrongQuantityAlert(quantity)) return

        var price = if (enteredPrice.isNotEmpty()) {
            enteredPrice.round(2).toDouble()
        } else {
            loadedProduct?.price ?: 0.0
        }
        price = loadedProduct?.convertPricePerDefaultUnit(price, currentUnit) ?: price

        val updatedProduct = loadedProduct?.copy(
            quantity = quantity,
            price = price,
            isDemand = binding?.boxIsDemand?.isChecked ?: false,
            isPacked = binding?.boxIsPacked?.isChecked ?: false,
        )
        sharedModel.selectProductAction(updatedProduct) {
            view?.findNavController()?.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}