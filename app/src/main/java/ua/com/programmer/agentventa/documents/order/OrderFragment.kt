package ua.com.programmer.agentventa.documents.order

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.ModelActivityOrderBinding
import ua.com.programmer.agentventa.fiscal.FiscalViewModel
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class OrderFragment: Fragment(), MenuProvider {

    private val viewModel: OrderViewModel by activityViewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val fiscalModel: FiscalViewModel by activityViewModels()
    private val navigationArgs: OrderFragmentArgs by navArgs()
    private var _binding: ModelActivityOrderBinding? = null
    private val binding get() = _binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setCurrentDocument(navigationArgs.orderGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ModelActivityOrderBinding.inflate(layoutInflater)
        binding?.lifecycleOwner = viewLifecycleOwner
        binding?.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OrderViewPagerAdapter(this)

        binding?.apply {
            menuSelectClient.setOnClickListener { openClientList() }
            menuSelectGoods.setOnClickListener { openProductList() }
            menuEditNotes.setOnClickListener { editNotes() }

            container.adapter = adapter
            TabLayoutMediator(orderTabs, container) { tab, position ->
                when (position) {
                    1 -> tab.text = getString(R.string.title_content)
                    2 -> tab.text = getString(R.string.title_previous)
                    else -> tab.text = getString(R.string.title_attributes)
                }
            }.attach()
        }
        binding?.menuSave?.setOnClickListener {
            if (viewModel.isFiscal()) {
                registerFiscalReceipt()
            } else {
                saveAndProcess()
            }
        }

        viewModel.document.observe(this.viewLifecycleOwner) {order ->
            var title = getString(R.string.order)
            var guid = ""
            var isProcessed = false
            order?.let {
                title += " ${order.number}"
                guid = order.guid
                isProcessed = order.isProcessed > 0
            }
            (activity as AppCompatActivity).supportActionBar?.title = title
            sharedModel.setDocumentGuid(guid)
            if (isProcessed) {
                binding?.orderBottomBar?.visibility = View.GONE
            } else {
                binding?.orderBottomBar?.visibility = View.VISIBLE
            }
            // will run once if document has GUID an client is not already set
            viewModel.setClient(navigationArgs.clientGuid)
        }
        viewModel.selectedPriceType.observe(this.viewLifecycleOwner) {
            sharedModel.setPrice(it)
        }
        sharedModel.selectClientAction = { client, popUp ->
            viewModel.onClientClick(client, popUp)
        }
        sharedModel.selectProductAction = { product, popUp ->
            viewModel.onProductClick(product, popUp)
        }

        viewModel.navigateToPage.observe(this.viewLifecycleOwner) {
            binding?.container?.setCurrentItem(it, false)
        }
        sharedModel.barcode.observe(this.viewLifecycleOwner) {
            if (it.isEmpty()) return@observe
            viewModel.onBarcodeRead(it) {
                Toast.makeText(requireContext(), getString(R.string.error_product_not_found), Toast.LENGTH_SHORT).show()
            }
            sharedModel.clearBarcode()
        }

    }

    override fun onDestroy() {
        sharedModel.clearActions()
        viewModel.onDestroy()
        _binding = null
        super.onDestroy()
    }

    private fun openProductList() {
        val orderGuid = viewModel.document.value?.guid ?: ""
        if (orderGuid.isEmpty()) return
        val action = OrderFragmentDirections.actionOrderFragmentToProductListFragment(
            groupGuid = "",
            modeSelect = true
        )
        view?.findNavController()?.navigate(action)
    }

    private fun openClientList() {
        val action = OrderFragmentDirections.actionOrderFragmentToClientListFragment(
            modeSelect = true,
        )
        view?.findNavController()?.navigate(action)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_order, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.print -> printDocument()
            R.id.copy_previous -> {
                if (viewModel.isNotEditable()) return false
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.warning))
                    .setMessage(getString(R.string.warn_order_content))
                    .setPositiveButton(getString(R.string.OK)) { _, _ ->
                        viewModel.copyPrevious { success ->
                            if (success) {
                                Toast.makeText(requireContext(), getString(R.string.data_copied), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.no_previous_document), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            R.id.edit_order -> {
                if (viewModel.isFiscal()) return false
                viewModel.enableEdit()
            }
            R.id.update_location -> {
                if (viewModel.isNotEditable()) return false
                viewModel.updateLocation {
                    Toast.makeText(requireContext(), getString(R.string.title_location_updates), Toast.LENGTH_SHORT).show()
                }
            }
            R.id.view_fiscal_receipt -> {
                if (!viewModel.isFiscal()) return false
                receiptPreview()
            }
            R.id.delete_order -> {
                if (viewModel.isFiscal()) return false
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.delete_data))
                    .setMessage(getString(R.string.text_erase_data))
                    .setPositiveButton(getString(R.string.delete_order)) { _, _ ->
                        viewModel.deleteDocument()
                        view?.findNavController()?.popBackStack()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            else -> return false
        }
        return true
    }

    private fun editNotes() {
        val alertDialog = AlertDialog.Builder(requireContext())
        val editText = EditText(requireContext())

        alertDialog.setTitle(R.string.doc_notes)
        //alertDialog.setMessage("Enter new text for TextView")

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT)
        editText.layoutParams = lp
        editText.setText(viewModel.document.value?.notes ?: "")
        alertDialog.setView(editText)

        alertDialog.setPositiveButton(R.string.save) { dialog, _ ->
            viewModel.onEditNotes(editText.text.toString())
            dialog.dismiss()
        }

        alertDialog.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        alertDialog.show()
    }

    private fun printDocument() {
        if (!sharedModel.options.printingEnabled) {
            Toast.makeText(requireContext(), getString(R.string.error_printing_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.canPrint()) {
            val guid = viewModel.getGuid()
            sharedModel.callPrintDocument(guid) {success ->
                if (!success) {
                    Toast.makeText(requireContext(), getString(R.string.error_while_print), Toast.LENGTH_SHORT).show()
                    return@callPrintDocument
                }
                openFile("$guid.pdf", "pdf")
            }
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.warning))
                .setMessage(getString(R.string.error_cannot_print))
                .setPositiveButton(getString(R.string.OK), null)
                .show()
        }
    }

    private fun notReadyToProcess(): Boolean {
        if (viewModel.notReadyToProcess()) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.warning))
                .setMessage(getString(R.string.error_cannot_process))
                .setPositiveButton(getString(R.string.OK), null)
                .show()
            return true
        }
        return false
    }

    private fun saveAndProcess() {
        if (notReadyToProcess()) return
        viewModel.saveDocument {
            if (it) {
                view?.findNavController()?.popBackStack()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.error))
                    .setMessage(getString(R.string.data_not_saved))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show()
            }
        }
    }

    private fun registerFiscalReceipt() {
        if (notReadyToProcess()) return
        if (fiscalServiceNotReady()) return
        binding?.menuSave?.visibility = View.GONE
        binding?.menuProgress?.visibility = View.VISIBLE
        fiscalModel.createReceipt(viewModel.getGuid()) { result ->
            if (result.success) {
                viewModel.saveDocument { saved ->
                    if (saved) {
                        receiptPreview(true)
                    } else {
                        AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.error))
                            .setMessage(getString(R.string.data_not_saved))
                            .setPositiveButton(getString(R.string.OK), null)
                            .show()
                    }
                }

            } else {
                val msg = "${getString(R.string.fiscal_service_error)}:\n${result.message}"
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.error))
                    .setMessage(msg)
                    .setPositiveButton(getString(R.string.OK), null)
                    .show()
            }
            binding?.menuSave?.visibility = View.VISIBLE
            binding?.menuProgress?.visibility = View.GONE
        }
    }

    private fun receiptPreview(autoClose: Boolean = false) {
        if (fiscalServiceNotReady()) return
        val guid = viewModel.getGuid()
        fiscalModel.getReceipt(guid) {
            if (it.success) {
                openFile("$guid.png", "png")
                if (autoClose) {
                    view?.findNavController()?.popBackStack()
                }
            } else {
                val msg = "${getString(R.string.fiscal_service_error)}:\n${it.message}"
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.error))
                    .setMessage(msg)
                    .setPositiveButton(getString(R.string.OK), null)
                    .show()
            }
        }
    }

    private fun fiscalServiceNotReady(): Boolean {
        if (fiscalModel.isNotReady()) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.warning))
                .setMessage(getString(R.string.error_fiscal_not_ready))
                .setPositiveButton(getString(R.string.action_settings)) { _, _ ->
                    view?.findNavController()?.navigate(R.id.action_orderFragment_to_fiscalFragment)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
            return true
        }
        return false
    }

    private fun openFile(fileName: String, extension: String) {
        val file = sharedModel.fileInCache(fileName)

        val mime = MimeTypeMap.getSingleton()
        val type = mime.getMimeTypeFromExtension(extension)
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "ua.com.programmer.agentventa",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, type)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class OrderViewPagerAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            1 -> OrderPageContent()
            2 -> OrderPageContentPrevious()
            else -> OrderPageTitle()
        }
        return fragment
    }

}