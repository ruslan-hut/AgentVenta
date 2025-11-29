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

        // Setup listeners first before observing account data
        setupManualConfigSwitch()
        setupFormatSpinner()
        setupWebSocketSection()
        setupSettingsSyncObservers()
        setupButtonListeners()

        viewModel.account.observe(this.viewLifecycleOwner) { account ->
            account?.let {
                binding.description.setText(account.description)
                binding.server.setText(account.dbServer)
                binding.dbName.setText(account.dbName)
                binding.dbUser.setText(account.dbUser)
                binding.dbPassword.setText(account.dbPassword)
                binding.syncEmail.setText(account.syncEmail)
                binding.accountId.text = account.getGuid()
                binding.accountGuid.text = account.guid
                binding.license.text = account.getLicense()

                // Set manual config switch based on useWebSocket field
                val isManualConfig = !account.useWebSocket
                binding.manualConfigSwitch.isChecked = isManualConfig

                // Update visibility based on account data format
                updateFieldsVisibility(isManualConfig)

                if (account.isDemo()) {
                    binding.description.isEnabled = false
                    binding.manualConfigSwitch.isEnabled = false
                    binding.server.isEnabled = false
                    binding.dbName.isEnabled = false
                    binding.dbUser.isEnabled = false
                    binding.dbPassword.isEnabled = false
                    binding.syncFormatSpinner.isEnabled = false
                    binding.syncEmail.isEnabled = false
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

        return binding.root
    }

    private fun setupManualConfigSwitch() {
        binding.manualConfigSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateFieldsVisibility(isChecked)

            // Update selected format (manual = HTTP, relay = WebSocket)
            viewModel.selectedFormat.value = if (isChecked) {
                Constants.SYNC_FORMAT_HTTP
            } else {
                Constants.SYNC_FORMAT_WEBSOCKET
            }
        }
    }

    private fun updateFieldsVisibility(isManualConfig: Boolean) {
        // Show/hide manual configuration section
        binding.manualConfigSection.visibility = if (isManualConfig) View.VISIBLE else View.GONE

        // Show/hide WebSocket section (visible when NOT in manual mode)
        binding.websocketSection.visibility = if (!isManualConfig) View.VISIBLE else View.GONE

        // Show/hide email field for settings sync (visible when NOT in manual mode)
        binding.syncEmailLayout.visibility = if (!isManualConfig) View.VISIBLE else View.GONE
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
        val isManualConfig = binding.manualConfigSwitch.isChecked

        _account?.let {
            val updated = it.copy(
                description = binding.description.text.toString().trim(),
                useWebSocket = !isManualConfig, // Store the preference
                dataFormat = if (isManualConfig) Constants.SYNC_FORMAT_HTTP else Constants.SYNC_FORMAT_WEBSOCKET,
                dbServer = if (isManualConfig) binding.server.text.toString().trim() else "",
                dbName = if (isManualConfig) binding.dbName.text.toString().trim() else "",
                dbUser = if (isManualConfig) binding.dbUser.text.toString().trim() else "",
                dbPassword = if (isManualConfig) binding.dbPassword.text.toString().trim() else "",
                syncEmail = if (!isManualConfig) binding.syncEmail.text.toString().trim() else "",
                relayServer = "", // Not used anymore
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
        // Spinner is hidden - manual mode always uses HTTP
        // Initialize adapter for backward compatibility
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(Constants.SYNC_FORMAT_HTTP)
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.syncFormatSpinner.adapter = adapter
    }

    private fun copyToClipboard(text: String) {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.pref_title_user_id), text)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(activity, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun setupWebSocketSection() {
        // Observe connection state text for display
        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            binding.connectionStatus.text = state
        }

        // Observe boolean connection states for button logic
        viewModel.isConnected.observe(viewLifecycleOwner) { connected ->
            binding.disconnectButton.isEnabled = connected
            binding.uploadSettingsButton.isEnabled = connected
            binding.downloadSettingsButton.isEnabled = connected

            // Update connect button based on connected and connecting states
            updateConnectButton()
        }

        viewModel.isConnecting.observe(viewLifecycleOwner) {
            // Update connect button based on connected and connecting states
            updateConnectButton()
        }
    }

    private fun updateConnectButton() {
        val connected = viewModel.isConnected.value ?: false
        val connecting = viewModel.isConnecting.value ?: false
        binding.connectButton.isEnabled = !connected && !connecting
    }

    private fun setupSettingsSyncObservers() {
        // Observe settings sync status
        viewModel.settingsSyncStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                binding.settingsSyncStatus.text = status
                binding.settingsSyncStatus.visibility = View.VISIBLE
            } else {
                binding.settingsSyncStatus.visibility = View.GONE
            }
        }
    }

    private fun setupButtonListeners() {
        // Connect button
        binding.connectButton.setOnClickListener {
            viewModel.connectWebSocket(_account)
        }

        // Disconnect button
        binding.disconnectButton.setOnClickListener {
            viewModel.disconnectWebSocket()
        }

        // Upload settings button
        binding.uploadSettingsButton.setOnClickListener {
            viewModel.uploadSettings(_account)
        }

        // Download settings button
        binding.downloadSettingsButton.setOnClickListener {
            viewModel.downloadSettings(_account)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}