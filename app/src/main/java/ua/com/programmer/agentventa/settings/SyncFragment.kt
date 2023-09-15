package ua.com.programmer.agentventa.settings

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
import ua.com.programmer.agentventa.dao.entity.getGuid
import ua.com.programmer.agentventa.databinding.SyncFragmentBinding
import ua.com.programmer.agentventa.shared.SharedViewModel

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
        binding = SyncFragmentBinding.inflate(inflater,container,false)
        binding?.lifecycleOwner = viewLifecycleOwner

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                val guid = viewModel.account?.guid ?: ""
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