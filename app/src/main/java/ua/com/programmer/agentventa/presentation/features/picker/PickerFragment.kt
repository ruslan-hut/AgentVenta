package ua.com.programmer.agentventa.presentation.features.picker

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import ua.com.programmer.agentventa.data.local.entity.LPrice
import ua.com.programmer.agentventa.data.local.entity.LProduct
import ua.com.programmer.agentventa.data.local.entity.convertPricePerDefaultUnit
import ua.com.programmer.agentventa.data.local.entity.convertQuantityPerDefaultUnit
import ua.com.programmer.agentventa.databinding.PickerFragmentBinding
import ua.com.programmer.agentventa.extensions.PriceCalculator
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.extensions.formatAsInt
import ua.com.programmer.agentventa.extensions.round
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
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

    // Guards to prevent infinite recalculation loops between percent ↔ discounted price
    private var updatingDiscountPercent = false
    private var updatingDiscountPrice = false
    private var canEditDiscount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedModel.sharedParams.observe(this) {
            currentPriceType = it.priceType
            viewModel.setProductParameters(navigationArgs.productGuid, it.docGuid, currentPriceType)
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
        binding?.editDiscountPrice?.setOnEditorActionListener { _, actionId, _ -> onEditTextAction(actionId) }

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
                    recalcDiscountPrice()
                }
                updatePriceHint(it.description)
            },
            onItemLongClicked = {},
        )
        priceRecycler?.adapter = adapter

        viewModel.priceList.observe(viewLifecycleOwner) { list ->
            loadedPriceList = list
            adapter.submitList(list)
            val hidePrices = list.isEmpty() || !sharedModel.options.allowPriceTypeChoose
            binding?.blockPriceList?.visibility = if (hidePrices) View.GONE else View.VISIBLE
            val currentPrice = list.firstOrNull { it.isCurrent }
            if (currentPrice != null) {
                updatePriceHint(currentPrice.description)
            }
        }

        viewModel.product.observe(viewLifecycleOwner) {
            loadedProduct = it
            currentUnit = it.unitType
            updateView()
            recalcDiscountPrice()
        }

        viewModel.discountPercent.observe(viewLifecycleOwner) {
            setDiscountPercentText(it)
            recalcDiscountPrice()
        }

        // Quantity changes → recalc total
        binding?.editQuantity?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateTotal() }
        })

        // Discount percent edited → recalc discounted price as visible text
        binding?.editDiscountPercent?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingDiscountPercent) return
                recalcDiscountPrice()
            }
        })

        // Discounted price edited → recalc percent
        binding?.editDiscountPrice?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingDiscountPrice) return
                recalcDiscountPercent()
            }
        })

        // When quantity loses focus with empty text, fill in the hint value
        binding?.editQuantity?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val editText = binding?.editQuantity ?: return@setOnFocusChangeListener
                if (editText.text.isNullOrEmpty()) {
                    val qty = loadedProduct?.quantity ?: 0.0
                    if (qty > 0) {
                        editText.setText(qty.formatAsInt(3, ""))
                    }
                }
            }
        }

        // When discount price loses focus with empty text, fill in the placeholder value
        binding?.editDiscountPrice?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val editText = binding?.editDiscountPrice ?: return@setOnFocusChangeListener
                if (editText.text.isNullOrEmpty()) {
                    val placeholder = binding?.itemDiscountPrice?.placeholderText?.toString()
                        ?: return@setOnFocusChangeListener
                    val price = placeholder.replace(",", ".").toDoubleOrNull()
                        ?: return@setOnFocusChangeListener
                    if (price > 0) {
                        updatingDiscountPrice = true
                        editText.setText(price.format(2))
                        updatingDiscountPrice = false
                    }
                }
            }
        }

        binding?.apply {
            editQuantity.requestFocus()
            editQuantity.postDelayed({
                val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this.editQuantity, InputMethodManager.SHOW_IMPLICIT)
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

            val currency = options.currency
            priceUnit.text = currency
            discountPriceUnit.text = currency

            // Price is always read-only (display only)
            editPrice.setText(product.price.format(2))
            editPrice.isEnabled = false

            // Discount fields: editable only when complexDiscounts and allowPriceEdit
            canEditDiscount = options.complexDiscounts && options.allowPriceEdit
            editDiscountPercent.isEnabled = canEditDiscount
            editDiscountPrice.isEnabled = canEditDiscount

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

    private fun updatePriceHint(priceTypeName: String) {
        val baseLabel = getString(R.string.attr_price)
        binding?.itemPrice?.hint = if (priceTypeName.isNotBlank()) {
            "$baseLabel: $priceTypeName"
        } else {
            baseLabel
        }
    }

    /**
     * Sets the discount percent text field without triggering the TextWatcher loop.
     */
    private fun setDiscountPercentText(percent: Double) {
        updatingDiscountPercent = true
        val text = if (percent == 0.0) "" else percent.format(2)
        binding?.editDiscountPercent?.setText(text)
        updatingDiscountPercent = false
    }

    /**
     * Sets the discount price as regular text value.
     */
    private fun setDiscountPriceHint(discountPrice: Double) {
        val formatted = discountPrice.format(2)
        updatingDiscountPrice = true
        binding?.editDiscountPrice?.setText(formatted)
        updatingDiscountPrice = false
        updateTotal()
    }

    /**
     * Recalculates the discounted price from the current base price and discount percent.
     */
    private fun recalcDiscountPrice() {
        val price = getCurrentPrice()
        val percent = getCurrentDiscountPercent()
        val discountPrice = price + price * percent / 100.0
        setDiscountPriceHint(discountPrice)
    }

    /**
     * Recalculates the discount percent from the current base price and discounted price.
     * Called when the discounted price field is edited.
     */
    private fun recalcDiscountPercent() {
        val price = getCurrentPrice()
        val discountPrice = getCurrentDiscountPrice()
        val percent = if (price != 0.0) {
            (discountPrice - price) / price * 100.0
        } else {
            0.0
        }
        updatingDiscountPercent = true
        val text = if (percent == 0.0) "" else percent.format(2)
        binding?.editDiscountPercent?.setText(text)
        updatingDiscountPercent = false
        updateTotal()
    }

    /**
     * Updates the total label: total = quantity × discountedPrice
     */
    private fun updateTotal() {
        val b = binding ?: return
        val quantity = getCurrentQuantity()
        val discountPrice = getCurrentDiscountPrice()
        val total = PriceCalculator.calculateLineWithDiscount(
            discountPrice, quantity, 0.0
        )
        b.totalValue.text = total.sum.format(2, "0.00")
    }

    private fun getCurrentPrice(): Double {
        val priceText = binding?.editPrice?.text.toString().replace(",", ".")
        return priceText.toDoubleOrNull() ?: (loadedProduct?.price ?: 0.0)
    }

    private fun getCurrentQuantity(): Double {
        val quantityText = binding?.editQuantity?.text.toString().replace(",", ".")
        return quantityText.toDoubleOrNull() ?: (loadedProduct?.quantity ?: 0.0)
    }

    private fun getCurrentDiscountPercent(): Double {
        val text = binding?.editDiscountPercent?.text.toString().replace(",", ".")
        return text.toDoubleOrNull() ?: 0.0
    }

    private fun getCurrentDiscountPrice(): Double {
        val text = binding?.editDiscountPrice?.text.toString().replace(",", ".")
        val fromText = text.toDoubleOrNull()
        if (fromText != null) return fromText
        // Fall back to placeholder value (calculated discount price)
        val placeholder = binding?.itemDiscountPrice?.placeholderText?.toString()?.replace(",", ".")
        return placeholder?.toDoubleOrNull() ?: getCurrentPrice()
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

    private fun sanitizeNumericInput(text: String): String {
        return text.replace(",", ".").trim()
            .trimEnd('.')
            .let { if (it.startsWith(".")) "0$it" else it }
    }

    private fun saveAndExit() {
        val enteredQuantity = sanitizeNumericInput(
            binding?.editQuantity?.text.toString()
        )
        val enteredPrice = sanitizeNumericInput(
            binding?.editPrice?.text.toString()
        )

        var quantity = utils.round(loadedProduct?.quantity ?: 0.0, 3)

        if (enteredQuantity.isNotEmpty()) {
            quantity = if (!enteredQuantity.contains(".") && enteredQuantity.startsWith("0")) {
                "0.$enteredQuantity".toDoubleOrNull() ?: 0.0
            } else {
                enteredQuantity.toDoubleOrNull() ?: 0.0
            }
        }
        quantity = loadedProduct?.convertQuantityPerDefaultUnit(quantity, currentUnit) ?: quantity

        if (wrongQuantityAlert(quantity)) return

        var price = if (enteredPrice.isNotEmpty()) {
            enteredPrice.round(2).toDoubleOrNull() ?: 0.0
        } else {
            loadedProduct?.price ?: 0.0
        }
        price = loadedProduct?.convertPricePerDefaultUnit(price, currentUnit) ?: price

        val discountPercent = getCurrentDiscountPercent()

        val updatedProduct = loadedProduct?.copy(
            quantity = quantity,
            price = price,
            isDemand = binding?.boxIsDemand?.isChecked ?: false,
            isPacked = binding?.boxIsPacked?.isChecked ?: false,
            discountPercent = discountPercent,
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
