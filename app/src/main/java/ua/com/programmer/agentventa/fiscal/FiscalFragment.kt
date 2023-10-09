package ua.com.programmer.agentventa.fiscal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.FiscalFragmentBinding
import ua.com.programmer.agentventa.extensions.toInt100
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class FiscalFragment: Fragment(), MenuProvider {

    private val viewModel: FiscalViewModel by activityViewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private var _binding: FiscalFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FiscalFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.initialise(sharedViewModel.options, sharedViewModel.cacheDir)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.error)
                .setMessage(R.string.fiscal_service_no_provider)
                .setPositiveButton(R.string.OK) { _,_ -> requireActivity().onBackPressed() }
                .show()
        }

        binding.btnCashierLogin.setOnClickListener {
            viewModel.onCashierLogin()
        }
        binding.btnCashierLogout.setOnClickListener {
            viewModel.onCashierLogout()
        }
        binding.btnCheckStatus.setOnClickListener {
            viewModel.onCheckStatus()
        }
        binding.btnOpenShift.setOnClickListener {
            viewModel.onOpenShift()
        }
        binding.btnXReport.setOnClickListener {
            viewModel.onCreateXReport()
        }
        binding.btnCloseShift.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.warning)
                .setMessage(R.string.close_shift_warn)
                .setPositiveButton(R.string.OK) { _,_ -> viewModel.onCloseShift()}
                .setNegativeButton(R.string.cancel) { _,_ -> }
                .show()
        }
        binding.btnServiceIn.setOnClickListener {
            val value = binding.serviceValue.text.toString().toInt100()
            if (value == 0 || value < 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.warning)
                    .setMessage(R.string.error_no_service_sum)
                    .setPositiveButton(R.string.OK) { _,_ -> }
                    .show()
            } else {
                binding.serviceValue.setText("")
                viewModel.createServiceReceipt(value)
            }
        }
        binding.btnServiceOut.setOnClickListener {
            val value = binding.serviceValue.text.toString().toInt100()
            if (value == 0 || value < 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.warning)
                    .setMessage(R.string.error_no_service_sum)
                    .setPositiveButton(R.string.OK) { _,_ -> }
                    .show()
            } else {
                binding.serviceValue.setText("")
                viewModel.createServiceReceipt(-value)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.INVISIBLE
            binding.btnCashierLogin.isEnabled = !it
            binding.btnCashierLogout.isEnabled = !it
            binding.btnCheckStatus.isEnabled = !it
            binding.btnOpenShift.isEnabled = !it
            binding.btnCloseShift.isEnabled = !it
            binding.btnXReport.isEnabled = !it
            binding.btnServiceIn.isEnabled = !it
            binding.btnServiceOut.isEnabled = !it
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            if (state.authorized) {
                binding.authGreen.visibility = View.VISIBLE
                binding.authRed.visibility = View.GONE
            } else {
                binding.authGreen.visibility = View.GONE
                binding.authRed.visibility = View.VISIBLE
            }
            if (state.offline) {
                binding.onlineGreen.visibility = View.GONE
                binding.onlineRed.visibility = View.VISIBLE
            } else {
                binding.onlineGreen.visibility = View.VISIBLE
                binding.onlineRed.visibility = View.GONE
            }
            if (state.shiftOpened) {
                binding.tvShiftOpened.text = state.shiftOpenedAt
                binding.shiftGreen.visibility = View.VISIBLE
                binding.shiftRed.visibility = View.GONE
            } else {
                binding.tvShiftOpened.text = ""
                binding.shiftGreen.visibility = View.GONE
                binding.shiftRed.visibility = View.VISIBLE
            }
        }

        viewModel.operationResult.observe(viewLifecycleOwner) {
            onResult(it)
        }

        binding.tvFiscalServiceProvider.text = viewModel.fiscalOptions.provider
        binding.tvCashier.text = viewModel.fiscalOptions.cashier
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_fiscal_fragment, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        TODO("Not yet implemented")
    }

    private fun onResult(result: OperationResult?) {
        val context = context ?: return
        result ?: return

        if (result.success) {
            if (result.fileId.isNotEmpty()) {
                openReportFile(result.fileId)
            } else if (result.receiptId.isNotEmpty()) {
                viewModel.getReceipt(result.receiptId)
            } else {
                Toast.makeText(context, R.string.operation_successful, Toast.LENGTH_SHORT).show()
            }
        } else {
            AlertDialog.Builder(context)
                .setTitle(R.string.error)
                .setMessage(result.message)
                .setPositiveButton(R.string.OK) { _,_ -> }
                .show()
        }
    }

    private fun openReportFile(id: String) {
        val context = context ?: return

        val file = sharedViewModel.fileInCache("$id.png")
        if (!file.exists()) {
            AlertDialog.Builder(context)
                .setTitle(R.string.warning)
                .setMessage(R.string.data_reading_error)
                .setPositiveButton(R.string.OK) { _,_ -> }
                .show()
            return
        }

        val mime = MimeTypeMap.getSingleton()
        val type = mime.getMimeTypeFromExtension("png")
        val uri = FileProvider.getUriForFile(
            context,
            "ua.com.programmer.agentventa",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, type)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearOperationResult()
        _binding = null
    }
}