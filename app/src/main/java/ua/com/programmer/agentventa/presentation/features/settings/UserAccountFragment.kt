package ua.com.programmer.agentventa.presentation.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.UserAccount
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.data.local.entity.getLicense
import ua.com.programmer.agentventa.data.local.entity.isDemo
import ua.com.programmer.agentventa.databinding.ActivityConnectionEditBinding
import ua.com.programmer.agentventa.utility.Constants

@AndroidEntryPoint
class UserAccountFragment: Fragment(), MenuProvider {

    private val viewModel: UserAccountViewModel by activityViewModels()
    private val navigationArgs: UserAccountFragmentArgs by navArgs()
    private var _binding: ActivityConnectionEditBinding? = null
    private val binding get() = _binding!!

    private var _account: UserAccount? = null
    private var _clickCounter = 0
    private var _clickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setCurrentAccount(navigationArgs.accountGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityConnectionEditBinding.inflate(layoutInflater)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel.account.observe(this.viewLifecycleOwner) {account ->
            account?.let {
                binding.description.setText(account.description)
                binding.server.setText(account.dbServer)
                binding.dbName.setText(account.dbName)
                binding.dbUser.setText(account.dbUser)
                binding.dbPassword.setText(account.dbPassword)
                binding.relayServer.setText(account.relayServer)
                binding.accountId.text = account.getGuid()
                binding.accountGuid.text = account.guid
                binding.license.text = account.getLicense()
                binding.syncFormatSpinner.setSelection(viewModel.formatSpinner.value?.indexOf(account.dataFormat) ?: 0)

                if (account.isDemo()) {
                    binding.description.isEnabled = false
                    binding.server.isEnabled = false
                    binding.dbName.isEnabled = false
                    binding.dbUser.isEnabled = false
                    binding.dbPassword.isEnabled = false
                    binding.syncFormatSpinner.isEnabled = false
                }

                _account = account
            }
        }

        binding.accountId.setOnLongClickListener {
            val id = _account?.guid ?: ""
            copyToClipboard(id)
            true
        }
        binding.accountId.setOnClickListener {
            if (System.currentTimeMillis() - _clickTime < 1000) {
                _clickCounter++
            }else{
                _clickCounter = 0
            }
            _clickTime = System.currentTimeMillis()
            if (_clickCounter > 9) {
                binding.fakeGuidLayout.visibility = View.VISIBLE
            }
        }

        setupFormatSpinner()

        // Observe format changes to show/hide WebSocket-specific fields
        viewModel.selectedFormat.observe(viewLifecycleOwner) { format ->
            updateFieldsVisibility(format)
        }

        return binding.root
    }

    private fun updateFieldsVisibility(format: String) {
        val isWebSocket = format == Constants.SYNC_FORMAT_WEBSOCKET

        // Show/hide WebSocket-specific fields
        binding.relayServerLayout.visibility = if (isWebSocket) View.VISIBLE else View.GONE
        binding.accountGuidLayout.visibility = if (isWebSocket) View.VISIBLE else View.GONE

        // Show/hide HTTP-specific fields
        binding.server.visibility = if (!isWebSocket) View.VISIBLE else View.GONE
        binding.dbName.visibility = if (!isWebSocket) View.VISIBLE else View.GONE
    }

    private fun finish() {
        view?.findNavController()?.popBackStack()
    }

    private fun saveAccount() {

        if (_account == null) {
            finish()
            return
        }

        val fakeGuid = binding.fakeGuid.text.toString().trim()

        _account?.let {
            val updated = it.copy(
                description = binding.description.text.toString().trim(),
                dataFormat = binding.syncFormatSpinner.selectedItem.toString(),
                dbServer = binding.server.text.toString().trim(),
                dbName = binding.dbName.text.toString().trim(),
                dbUser = binding.dbUser.text.toString().trim(),
                dbPassword = binding.dbPassword.text.toString().trim(),
                relayServer = binding.relayServer.text.toString().trim(),
                guid = fakeGuid.ifEmpty { it.guid }
            )
            viewModel.saveAccount(updated) {
                finish()
            }
        }

    }

    private fun deleteAccount() {
        // show alert dialog for confirmation
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_data))
            .setMessage(getString(R.string.text_erase_data))
            .setPositiveButton(getString(R.string.delete_order)) { _, _ ->
                _account?.let {
                    viewModel.deleteAccount(it.guid) {
                        finish()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_connection_edit, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.delete -> {
                deleteAccount()
            }
            R.id.save -> {
                saveAccount()
            }
            else -> return false
        }
        return true
    }

    private fun setupFormatSpinner() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, viewModel.formatSpinner.value!!)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.syncFormatSpinner.adapter = adapter
        binding.syncFormatSpinner.setSelection(adapter.getPosition(viewModel.selectedFormat.value))

        binding.syncFormatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                viewModel.selectedFormat.value = viewModel.formatSpinner.value!![position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.pref_title_user_id), text)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(activity, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}