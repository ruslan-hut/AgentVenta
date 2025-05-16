package ua.com.programmer.agentventa.catalogs.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
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
import ua.com.programmer.agentventa.dao.entity.LClient
import ua.com.programmer.agentventa.databinding.ClientListFragmentBinding
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class ClientListFragment: Fragment(), MenuProvider {

    private val viewModel: ClientListViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ClientListFragmentArgs by navArgs()
    private var _binding: ClientListFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setCurrentGroup(navigationArgs.groupGuid)
        viewModel.setSelectMode(navigationArgs.modeSelect)
        viewModel.setCompany(navigationArgs.companyGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ClientListFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.clientsList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        binding.clientsSwipe.setOnRefreshListener {
            //TODO on swipe listener
            binding.clientsSwipe.isRefreshing = false
        }

        val adapter = ClientListAdapter { onItemClick(it) }
        recyclerView.adapter = adapter
        viewModel.clients.observe(this.viewLifecycleOwner) {
            adapter.submitList(it)
        }

        viewModel.currentGroup.observe(this.viewLifecycleOwner) {
            val desc = it?.description ?: ""
            val title = desc.ifBlank { getString(R.string.header_clients_list) }
            (requireActivity() as AppCompatActivity).supportActionBar?.title = title
        }
    }

    private fun onItemClick(client: LClient) {
        if (client.isGroup) {
            val action = ClientListFragmentDirections.actionClientListFragmentSelf(
                groupGuid = client.guid,
                modeSelect = navigationArgs.modeSelect,
                companyGuid = navigationArgs.companyGuid,
            )
            view?.findNavController()?.navigate(action)
        } else if (navigationArgs.modeSelect) {
            sharedModel.selectClientAction(client) {
                view?.findNavController()?.popBackStack(R.id.orderFragment, false)
            }
        } else {
            val action = ClientListFragmentDirections.actionClientListFragmentToClientMenuFragment(
                clientGuid = client.guid
            )
            view?.findNavController()?.navigate(action)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_clients, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_search -> {
                viewModel.toggleSearchVisibility()
            }

            else -> return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}