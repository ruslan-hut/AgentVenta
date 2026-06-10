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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.data.local.entity.getGuid
import ua.com.programmer.agentventa.databinding.SyncFragmentBinding
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import ua.com.programmer.agentventa.presentation.common.viewmodel.SyncEvent

@AndroidEntryPoint
class SyncFragment: Fragment(), MenuProvider {

    private val viewModel: SyncViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var binding: SyncFragmentBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding = SyncFragmentBinding.inflate(inflater, container, false)
        binding?.lifecycleOwner = viewLifecycleOwner
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
    }

    private fun setupObservers() {
        sharedViewModel.currentAccount.observe(viewLifecycleOwner) { account ->
            viewModel.account = account
            binding?.apply {
                description.text = account.description
                server.text = account.dbServer
                user.text = account.dbUser
                database.text = account.dbName
                format.text = account.getGuid()
            }
            if (!viewModel.isUpdated) {
                sharedViewModel.callFullSync {
                    binding?.status?.text = getString(R.string.data_updated)
                    viewModel.isUpdated = true
                }
            }
        }
        sharedViewModel.isRefreshing.observe(viewLifecycleOwner) {
            binding?.progressBar?.visibility = if (it) View.VISIBLE else View.INVISIBLE
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.syncEvents.collect { event ->
                    val (message, duration) = when (event) {
                        is SyncEvent.Success -> getString(R.string.data_updated) to Snackbar.LENGTH_SHORT
                        is SyncEvent.Error -> getString(R.string.snackbar_sync_error, event.message) to Snackbar.LENGTH_LONG
                        is SyncEvent.Progress -> return@collect
                    }
                    view?.let { Snackbar.make(it, message, duration).show() }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_sync_fragment, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_sync -> {
                sharedViewModel.callFullSync {
                    binding?.status?.text = getString(R.string.data_updated)
                    viewModel.isUpdated = true
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
