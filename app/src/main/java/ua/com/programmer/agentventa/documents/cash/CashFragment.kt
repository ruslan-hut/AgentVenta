package ua.com.programmer.agentventa.documents.cash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.getSumFormatted
import ua.com.programmer.agentventa.databinding.CashFragmentBinding
import ua.com.programmer.agentventa.shared.SharedViewModel
import ua.com.programmer.agentventa.utility.Constants

@AndroidEntryPoint
class CashFragment: Fragment(), MenuProvider {

    private val viewModel: CashViewModel by activityViewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: CashFragmentArgs by navArgs()
    private var _binding: CashFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setCurrentDocument(navigationArgs.cashGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CashFragmentBinding.inflate(layoutInflater)
        binding.lifecycleOwner = viewLifecycleOwner

        binding.isFiscal.setOnCheckedChangeListener { _, isChecked ->
            val isFiscal = if(isChecked) 1 else 0
            viewModel.onEditFiscal(isFiscal)
        }

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel.document.observe(this.viewLifecycleOwner) {
            var title = getString(R.string.header_cash_list)
            if (it != null) {
                title += " ${it.number}"
            }
            (activity as AppCompatActivity).supportActionBar?.title = title
            sharedModel.setDocumentGuid(
                Constants.DOCUMENT_CASH,
                it?.guid ?: "",
                it?.companyGuid ?: "",
                )
//            if (it.isProcessed > 0) {
//                binding.orderBottomBar.visibility = View.GONE
//            } else {
//                binding.orderBottomBar.visibility = View.VISIBLE
//            }
        }

        sharedModel.selectClientAction = { client, popUp ->
            viewModel.onClientClick(client, popUp)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.document.observe(this.viewLifecycleOwner) {
            val options = sharedModel.options
            binding.apply {
                docNumber.text = it.number.toString()
                docDate.text = it.date
                docCompany.text = it.company
                docClient.text = it.client
                docTotalPrice.text = it.getSumFormatted()
                isFiscal.text = it.fiscalNumber.toString()
                isFiscal.isChecked = it.isFiscal > 0
                docParentDocument.text = it.referenceGuid
                docNotes.text = it.notes

                titleCompany.visibility = if (options.useCompanies) View.VISIBLE else View.GONE
            }
        }
        binding.docCompany.setOnClickListener {
            openCompanies()
        }
        binding.docClient.setOnClickListener {
            openClients()
        }
    }

    fun openClients() {
        val action = CashFragmentDirections.actionCashFragmentToClientListFragment(
            modeSelect = true
        )
        view?.findNavController()?.navigate(action)
    }

    fun openCompanies() {
        val action = CashFragmentDirections.actionCashFragmentToCompanyListFragment()
        view?.findNavController()?.navigate(action)
    }

    override fun onDestroy() {
        sharedModel.clearActions()
        viewModel.onDestroy()
        _binding = null
        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_document, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.edit_order -> viewModel.enableEdit()
            R.id.delete_document -> {
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

}
