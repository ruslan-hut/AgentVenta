package ua.com.programmer.agentventa.presentation.features.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.databinding.FragmentWebsocketTestBinding
import ua.com.programmer.agentventa.databinding.SyncFragmentBinding
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import ua.com.programmer.agentventa.presentation.features.websocket.WebSocketTestViewModel

@AndroidEntryPoint
class SyncFragment: Fragment(), MenuProvider {

    private val viewModel: SyncViewModel by viewModels()
    private val webSocketViewModel: WebSocketTestViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Legacy HTTP binding
    private var httpBinding: SyncFragmentBinding? = null
    // WebSocket binding
    private var wsBinding: FragmentWebsocketTestBinding? = null

    private var useWebSocket = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Check current account's connection mode
        // Note: We need to get this synchronously for layout inflation
        // The actual value will be confirmed in onViewCreated
        val currentAccount = sharedViewModel.currentAccount.value
        useWebSocket = currentAccount?.useWebSocket ?: false

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return if (useWebSocket) {
            wsBinding = FragmentWebsocketTestBinding.inflate(inflater, container, false)
            wsBinding?.root
        } else {
            httpBinding = SyncFragmentBinding.inflate(inflater, container, false)
            httpBinding?.lifecycleOwner = viewLifecycleOwner
            httpBinding?.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (useWebSocket) {
            setupWebSocketObservers()
            setupWebSocketListeners()
        } else {
            setupHttpObservers()
        }
    }

    override fun onResume() {
        super.onResume()
        if (useWebSocket) {
            webSocketViewModel.refreshPendingData()
        }
    }

    private fun setupHttpObservers() {
        sharedViewModel.currentAccount.observe(viewLifecycleOwner) { account ->
            viewModel.account = account
            httpBinding?.apply {
                description.text = account.description
                server.text = account.dbServer
                user.text = account.dbUser
                database.text = account.dbName
                format.text = account.getGuid()
            }
            if (!viewModel.isUpdated) {
                sharedViewModel.callFullSync {
                    httpBinding?.status?.text = getString(R.string.data_updated)
                    viewModel.isUpdated = true
                }
            }
        }
        sharedViewModel.isRefreshing.observe(viewLifecycleOwner) {
            httpBinding?.progressBar?.visibility = if (it) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun setupWebSocketObservers() {
        // Store account reference for menu actions
        sharedViewModel.currentAccount.observe(viewLifecycleOwner) { account ->
            viewModel.account = account
        }

        webSocketViewModel.connectionState.observe(viewLifecycleOwner) { state ->
            wsBinding?.connectionStatusText?.text = state
        }

        webSocketViewModel.pendingDataInfo.observe(viewLifecycleOwner) { info ->
            wsBinding?.pendingDataText?.text = info
        }

        webSocketViewModel.lastSyncTime.observe(viewLifecycleOwner) { time ->
            wsBinding?.lastSyncText?.text = time
        }

        webSocketViewModel.isSyncing.observe(viewLifecycleOwner) { isSyncing ->
            wsBinding?.syncNowButton?.apply {
                isEnabled = !isSyncing
                text = if (isSyncing) {
                    getString(R.string.status_downloading)
                } else {
                    getString(R.string.websocket_sync_now)
                }
            }
        }
    }

    private fun setupWebSocketListeners() {
        wsBinding?.syncNowButton?.setOnClickListener {
            webSocketViewModel.syncNow()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        httpBinding = null
        wsBinding = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        if (useWebSocket) {
            menuInflater.inflate(R.menu.menu_sync_fragment_websocket, menu)
        } else {
            menuInflater.inflate(R.menu.menu_sync_fragment, menu)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_sync -> {
                if (useWebSocket) {
                    webSocketViewModel.syncNow()
                } else {
                    sharedViewModel.callFullSync {
                        httpBinding?.status?.text = getString(R.string.data_updated)
                        viewModel.isUpdated = true
                    }
                }
            }
            R.id.account_settings -> {
                val guid = sharedViewModel.currentAccount.value?.guid ?: ""
                if (guid.isNotEmpty()) {
                    val action = SyncFragmentDirections.actionSyncFragmentToUserAccountFragment(
                        accountGuid = guid
                    )
                    view?.findNavController()?.navigate(action)
                }
            }
            R.id.event_log -> {
                val action = SyncFragmentDirections.actionSyncFragmentToLogFragment()
                view?.findNavController()?.navigate(action)
            }
            else -> return false
        }
        return true
    }
}